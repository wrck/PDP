package com.pdp.shared.concurrency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ETagTest {

    @Test
    void buildsFromRevision() {
        ETag etag = ETag.of(Revision.of(5));
        assertThat(etag.value()).isEqualTo("revision:5");
    }

    @Test
    void parsesRevisionFromIfMatch() {
        Revision parsed = ETag.parseRevision("revision:7");
        assertThat(parsed.value()).isEqualTo(7);
    }

    @Test
    void parsesQuotedIfMatch() {
        Revision parsed = ETag.parseRevision("\"revision:12\"");
        assertThat(parsed.value()).isEqualTo(12);
    }

    @Test
    void rejectsInvalidFormat() {
        assertThatThrownBy(() -> ETag.parseRevision("v3"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ETag.parseRevision("revision:abc"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ETag.parseRevision(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revisionIncrements() {
        Revision r = Revision.initial();
        assertThat(r.value()).isEqualTo(1);
        assertThat(r.next().value()).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveRevision() {
        assertThatThrownBy(() -> Revision.of(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Revision.of(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
