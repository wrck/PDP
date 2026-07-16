package com.pdp.experience.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class S3CompatibleObjectStorageAdapterTest {
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");
    private static final String CHECKSUM = "a".repeat(64);

    @Test
    void cleanObjectIsPromotedAndReceivesShortLivedDownloadUrl() {
        FakeS3Client client = new FakeS3Client();
        List<ObjectLocation> quarantined = new ArrayList<>();
        var adapter =
                adapter(
                        client,
                        location ->
                                new VirusScanResult(
                                        VirusScanResult.Verdict.CLEAN,
                                        "clamav",
                                        "20260717",
                                        "",
                                        NOW),
                        quarantined);

        var ticket =
                adapter.createUploadTicket(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        12,
                        "application/pdf",
                        CHECKSUM,
                        Duration.ofMinutes(2));
        assertThat(ticket.uploadUrl().requiredHeaders().get("x-amz-checksum-sha256"))
                .isEqualTo("qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqo=");
        client.metadata.put(
                ticket.stagingLocation(),
                new ObjectMetadata(12, "application/pdf", CHECKSUM, "staged-etag"));

        StoredObject stored = adapter.completeUpload(ticket);
        SignedObjectUrl download = adapter.issueDownloadUrl(stored, Duration.ofMinutes(5));

        assertThat(stored.scanStatus()).isEqualTo(StoredObject.ScanStatus.CLEAN);
        assertThat(stored.location().key()).startsWith("objects/");
        assertThat(client.deleted).containsExactly(ticket.stagingLocation());
        assertThat(download.method()).isEqualTo(SignedObjectUrl.Method.GET);
        assertThat(download.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(quarantined).isEmpty();
    }

    @Test
    void maliciousOrScannerFailureRemainsQuarantinedAndCannotBeDownloaded() {
        FakeS3Client client = new FakeS3Client();
        List<ObjectLocation> quarantined = new ArrayList<>();
        var adapter =
                adapter(
                        client,
                        location -> {
                            throw new IllegalStateException("scanner unavailable");
                        },
                        quarantined);
        var ticket =
                adapter.createUploadTicket(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "text/plain",
                        CHECKSUM,
                        Duration.ofMinutes(1));
        client.metadata.put(
                ticket.stagingLocation(),
                new ObjectMetadata(1, "text/plain", CHECKSUM, "staged-etag"));

        StoredObject stored = adapter.completeUpload(ticket);

        assertThat(stored.scanStatus()).isEqualTo(StoredObject.ScanStatus.QUARANTINED);
        assertThat(stored.scanResult().verdict()).isEqualTo(VirusScanResult.Verdict.ERROR);
        assertThat(quarantined).containsExactly(ticket.stagingLocation());
        assertThatThrownBy(() -> adapter.issueDownloadUrl(stored, Duration.ofMinutes(1)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsOverlongUrlAndMismatchedObjectMetadata() {
        FakeS3Client client = new FakeS3Client();
        var adapter =
                adapter(
                        client,
                        location ->
                                new VirusScanResult(
                                        VirusScanResult.Verdict.CLEAN,
                                        "clamav",
                                        "20260717",
                                        "",
                                        NOW),
                        new ArrayList<>());

        assertThatThrownBy(
                        () ->
                                adapter.createUploadTicket(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        1,
                                        "text/plain",
                                        CHECKSUM,
                                        Duration.ofMinutes(6)))
                .isInstanceOf(IllegalArgumentException.class);

        var ticket =
                adapter.createUploadTicket(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        "text/plain",
                        CHECKSUM,
                        Duration.ofMinutes(1));
        client.metadata.put(
                ticket.stagingLocation(),
                new ObjectMetadata(2, "text/plain", CHECKSUM, "staged-etag"));

        assertThatThrownBy(() -> adapter.completeUpload(ticket))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("大小");
    }

    private static S3CompatibleObjectStorageAdapter adapter(
            FakeS3Client client,
            VirusScanner scanner,
            List<ObjectLocation> quarantined) {
        return new S3CompatibleObjectStorageAdapter(
                client,
                scanner,
                (location, metadata, result) -> quarantined.add(location),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class FakeS3Client implements S3ObjectClient {
        private final Map<ObjectLocation, ObjectMetadata> metadata = new HashMap<>();
        private final List<ObjectLocation> deleted = new ArrayList<>();

        @Override
        public SignedObjectUrl presignPut(
                ObjectLocation location,
                Instant expiresAt,
                Map<String, String> requiredHeaders) {
            return signed(location, SignedObjectUrl.Method.PUT, expiresAt, requiredHeaders);
        }

        @Override
        public SignedObjectUrl presignGet(ObjectLocation location, Instant expiresAt) {
            return signed(location, SignedObjectUrl.Method.GET, expiresAt, Map.of());
        }

        @Override
        public ObjectMetadata head(ObjectLocation location) {
            return metadata.get(location);
        }

        @Override
        public ObjectMetadata copy(ObjectLocation source, String targetKey) {
            ObjectMetadata sourceMetadata = metadata.get(source);
            ObjectMetadata copied =
                    new ObjectMetadata(
                            sourceMetadata.size(),
                            sourceMetadata.mediaType(),
                            sourceMetadata.checksumSha256(),
                            "promoted-etag");
            metadata.put(
                    new ObjectLocation(source.workspaceId(), targetKey, copied.eTag()), copied);
            return copied;
        }

        @Override
        public void delete(ObjectLocation location) {
            deleted.add(location);
            metadata.remove(location);
        }

        private static SignedObjectUrl signed(
                ObjectLocation location,
                SignedObjectUrl.Method method,
                Instant expiresAt,
                Map<String, String> headers) {
            return new SignedObjectUrl(
                    URI.create("https://objects.example.test/" + location.key()),
                    method,
                    expiresAt,
                    headers);
        }
    }
}
