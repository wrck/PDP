package com.pdp.api.domainconfig;

import com.pdp.shared.page.SignedKeysetCursorCodec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DomainPackageApiConfiguration.CursorProperties.class)
class DomainPackageApiConfiguration {

  @Bean
  @ConditionalOnMissingBean
  Clock platformClock() {
    return Clock.systemUTC();
  }

  @Bean
  SignedKeysetCursorCodec domainPackageCursorCodec(
      CursorProperties properties, Clock clock) {
    String activeKeyId = requireText(properties.activeKeyId(), "活动游标签名键 ID");
    var keyset = new LinkedHashMap<String, byte[]>();
    keyset.put(activeKeyId, decodeKey(properties.activeKeyBase64(), activeKeyId));
    properties
        .previousKeys()
        .forEach((keyId, encoded) -> keyset.put(keyId, decodeKey(encoded, keyId)));
    Duration ttl = properties.ttl() == null ? Duration.ofHours(1) : properties.ttl();
    if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofHours(24)) > 0) {
      throw new IllegalArgumentException("游标签名有效期必须大于零且不超过 24 小时");
    }
    return new SignedKeysetCursorCodec(activeKeyId, keyset, ttl, clock);
  }

  private static byte[] decodeKey(String encoded, String keyId) {
    try {
      byte[] key = Base64.getDecoder().decode(requireText(encoded, "游标签名键 " + keyId));
      if (key.length < 32) {
        throw new IllegalArgumentException("游标签名键至少需要 256 位: " + keyId);
      }
      return key;
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("游标签名键配置无效: " + keyId, exception);
    }
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    return value;
  }

  @ConfigurationProperties(prefix = "pdp.domain-package.cursor")
  public record CursorProperties(
      String activeKeyId,
      String activeKeyBase64,
      Map<String, String> previousKeys,
      Duration ttl) {
    public CursorProperties {
      previousKeys = Map.copyOf(previousKeys == null ? Map.of() : previousKeys);
    }
  }
}
