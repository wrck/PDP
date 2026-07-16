package com.pdp.experience.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Base64;

/**
 * 对象存储安全适配器：上传先隔离，校验后扫描，只有安全对象才能提升并签发短时地址。
 */
public final class S3CompatibleObjectStorageAdapter {
    public static final Duration MAX_SIGNED_URL_TTL = Duration.ofMinutes(5);

    private static final String QUARANTINE_PREFIX = "quarantine/";
    private static final String ACTIVE_PREFIX = "objects/";

    private final S3ObjectClient client;
    private final VirusScanner scanner;
    private final QuarantineRecorder quarantineRecorder;
    private final Clock clock;

    public S3CompatibleObjectStorageAdapter(
            S3ObjectClient client,
            VirusScanner scanner,
            QuarantineRecorder quarantineRecorder,
            Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.scanner = Objects.requireNonNull(scanner);
        this.quarantineRecorder = Objects.requireNonNull(quarantineRecorder);
        this.clock = Objects.requireNonNull(clock);
    }

    public UploadTicket createUploadTicket(
            UUID workspaceId,
            UUID fileVersionId,
            long expectedSize,
            String mediaType,
            String expectedSha256,
            Duration ttl) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(fileVersionId, "fileVersionId");
        if (expectedSize < 0) {
            throw new IllegalArgumentException("文件大小不能为负数");
        }
        requireText(mediaType, "mediaType");
        String checksum = normalizeSha256(expectedSha256);
        Instant expiresAt = expiresAt(ttl);
        String uploadId = UUID.randomUUID().toString();
        String key =
                QUARANTINE_PREFIX
                        + workspaceId
                        + "/"
                        + fileVersionId
                        + "/"
                        + uploadId;
        ObjectLocation staging = new ObjectLocation(workspaceId, key, "pending");
        Map<String, String> headers =
                Map.of(
                        "content-type", mediaType,
                        "x-amz-checksum-sha256",
                                Base64.getEncoder()
                                        .encodeToString(HexFormat.of().parseHex(checksum)));
        SignedObjectUrl uploadUrl =
                requireSignedUrl(
                        client.presignPut(staging, expiresAt, headers),
                        SignedObjectUrl.Method.PUT,
                        expiresAt);
        return new UploadTicket(
                staging, expectedSize, mediaType, checksum, uploadUrl);
    }

    public StoredObject completeUpload(UploadTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        requireQuarantineLocation(ticket.stagingLocation());
        if (!clock.instant().isBefore(ticket.uploadUrl().expiresAt())) {
            throw new IllegalStateException("上传凭据已过期");
        }

        ObjectMetadata actual =
                Objects.requireNonNull(
                        client.head(ticket.stagingLocation()), "对象存储未返回上传对象元数据");
        verifyMetadata(ticket, actual);
        VirusScanResult scanResult = scanSafely(ticket.stagingLocation());
        if (scanResult.verdict() != VirusScanResult.Verdict.CLEAN) {
            quarantineRecorder.record(ticket.stagingLocation(), actual, scanResult);
            return new StoredObject(
                    ticket.stagingLocation(),
                    actual,
                    StoredObject.ScanStatus.QUARANTINED,
                    scanResult);
        }

        String activeKey =
                ACTIVE_PREFIX
                        + ticket.stagingLocation().workspaceId()
                        + "/"
                        + activeSuffix(ticket.stagingLocation().key());
        ObjectMetadata promoted =
                Objects.requireNonNull(
                        client.copy(ticket.stagingLocation(), activeKey), "对象提升未返回元数据");
        client.delete(ticket.stagingLocation());
        ObjectLocation active =
                new ObjectLocation(
                        ticket.stagingLocation().workspaceId(),
                        activeKey,
                        promoted.eTag());
        return new StoredObject(active, promoted, StoredObject.ScanStatus.CLEAN, scanResult);
    }

    public SignedObjectUrl issueDownloadUrl(StoredObject storedObject, Duration ttl) {
        Objects.requireNonNull(storedObject, "storedObject");
        if (storedObject.scanStatus() != StoredObject.ScanStatus.CLEAN
                || !storedObject.location().key().startsWith(ACTIVE_PREFIX)) {
            throw new SecurityException("未通过扫描或已隔离对象不得签发下载地址");
        }
        Instant requestedExpiry = expiresAt(ttl);
        return requireSignedUrl(
                client.presignGet(storedObject.location(), requestedExpiry),
                SignedObjectUrl.Method.GET,
                requestedExpiry);
    }

    public StoredObject quarantine(StoredObject storedObject, String reason) {
        Objects.requireNonNull(storedObject, "storedObject");
        if (storedObject.scanStatus() != StoredObject.ScanStatus.CLEAN
                || !storedObject.location().key().startsWith(ACTIVE_PREFIX)) {
            throw new IllegalStateException("仅正式区安全对象允许执行人工隔离");
        }
        String quarantineKey =
                QUARANTINE_PREFIX
                        + storedObject.location().workspaceId()
                        + "/manual/"
                        + UUID.randomUUID();
        ObjectMetadata copied =
                Objects.requireNonNull(
                        client.copy(storedObject.location(), quarantineKey), "对象隔离未返回元数据");
        client.delete(storedObject.location());
        VirusScanResult result =
                new VirusScanResult(
                        VirusScanResult.Verdict.SUSPICIOUS,
                        "manual-quarantine",
                        "n/a",
                        requireText(reason, "reason"),
                        clock.instant());
        ObjectLocation quarantined =
                new ObjectLocation(
                        storedObject.location().workspaceId(),
                        quarantineKey,
                        copied.eTag());
        quarantineRecorder.record(quarantined, copied, result);
        return new StoredObject(
                quarantined, copied, StoredObject.ScanStatus.QUARANTINED, result);
    }

    private VirusScanResult scanSafely(ObjectLocation location) {
        try {
            VirusScanResult result = scanner.scan(location);
            return Objects.requireNonNull(result, "病毒扫描结果不能为空");
        } catch (RuntimeException exception) {
            return new VirusScanResult(
                    VirusScanResult.Verdict.ERROR,
                    "scanner",
                    "unknown",
                    safeReason(exception),
                    clock.instant());
        }
    }

    private static void verifyMetadata(UploadTicket ticket, ObjectMetadata actual) {
        if (actual.size() != ticket.expectedSize()) {
            throw new IllegalStateException("上传对象大小与声明不一致");
        }
        if (!actual.mediaType().equalsIgnoreCase(ticket.mediaType())) {
            throw new IllegalStateException("上传对象媒体类型与声明不一致");
        }
        byte[] expected = HexFormat.of().parseHex(ticket.expectedSha256());
        byte[] actualDigest = HexFormat.of().parseHex(actual.checksumSha256());
        if (!MessageDigest.isEqual(expected, actualDigest)) {
            throw new IllegalStateException("上传对象 SHA-256 与声明不一致");
        }
    }

    private Instant expiresAt(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_SIGNED_URL_TTL) > 0) {
            throw new IllegalArgumentException("签名 URL 有效期必须大于零且不超过五分钟");
        }
        return clock.instant().plus(ttl);
    }

    private SignedObjectUrl requireSignedUrl(
            SignedObjectUrl signedUrl,
            SignedObjectUrl.Method expectedMethod,
            Instant requestedExpiry) {
        Objects.requireNonNull(signedUrl, "对象存储未返回签名地址");
        if (signedUrl.method() != expectedMethod) {
            throw new IllegalStateException("对象存储返回了错误的签名请求方法");
        }
        if (signedUrl.expiresAt().isAfter(requestedExpiry)
                || !signedUrl.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException("对象存储返回的签名地址有效期不符合策略");
        }
        return signedUrl;
    }

    private static void requireQuarantineLocation(ObjectLocation location) {
        if (!location.key().startsWith(QUARANTINE_PREFIX)) {
            throw new SecurityException("仅允许完成隔离区上传");
        }
    }

    private static String activeSuffix(String quarantineKey) {
        return quarantineKey.substring(QUARANTINE_PREFIX.length());
    }

    private static String normalizeSha256(String value) {
        String normalized = requireText(value, "expectedSha256").toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedSha256 必须是 64 位十六进制摘要");
        }
        return normalized;
    }

    private static String safeReason(RuntimeException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 500) {
            return value;
        }
        return new String(bytes, 0, 500, StandardCharsets.UTF_8);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }

    public record UploadTicket(
            ObjectLocation stagingLocation,
            long expectedSize,
            String mediaType,
            String expectedSha256,
            SignedObjectUrl uploadUrl) {

        public UploadTicket {
            Objects.requireNonNull(stagingLocation, "stagingLocation");
            if (expectedSize < 0) {
                throw new IllegalArgumentException("expectedSize 不能为负数");
            }
            mediaType = requireText(mediaType, "mediaType");
            expectedSha256 = normalizeSha256(expectedSha256);
            Objects.requireNonNull(uploadUrl, "uploadUrl");
            if (uploadUrl.method() != SignedObjectUrl.Method.PUT) {
                throw new IllegalArgumentException("上传凭据必须使用 PUT");
            }
        }
    }
}
