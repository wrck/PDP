package com.pdp.experience.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SearchAnalyzerTest {

    @Test
    void normalizesLatinAndCreatesCjkBigramsForSubstringMatching() {
        SearchAnalyzer analyzer = new SearchAnalyzer();

        assertThat(analyzer.analyze(" PDP-Platform ")).containsExactlyInAnyOrder("pdp", "platform");
        Set<String> documentTerms = analyzer.analyze("网络设备割接");
        assertThat(documentTerms).containsAll(analyzer.analyze("设备割接"));
    }
}
