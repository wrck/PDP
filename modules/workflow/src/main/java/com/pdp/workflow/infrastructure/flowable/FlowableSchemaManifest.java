package com.pdp.workflow.infrastructure.flowable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public record FlowableSchemaManifest(
        String engineVersion,
        String databaseProduct,
        String targetVersion,
        List<String> createScripts,
        String previousVersion,
        List<String> previousUpgradeScripts) {

    private static final String RESOURCE =
            "db/flowable/mysql/flowable-schema-manifest.properties";

    public FlowableSchemaManifest {
        createScripts = List.copyOf(createScripts);
        previousUpgradeScripts = List.copyOf(previousUpgradeScripts);
    }

    public static FlowableSchemaManifest load() {
        Properties properties = new Properties();
        try (InputStream input = resource(RESOURCE)) {
            properties.load(input);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取 Flowable schema 清单", ex);
        }
        FlowableSchemaManifest manifest = new FlowableSchemaManifest(
                required(properties, "engine.version"),
                required(properties, "database.product"),
                required(properties, "target.version"),
                list(properties, "create.scripts"),
                required(properties, "previous.version"),
                list(properties, "previous.upgrade.scripts"));
        manifest.allScripts().forEach(FlowableSchemaManifest::resourceExists);
        return manifest;
    }

    public List<String> upgradeScriptsFrom(String sourceVersion) {
        if (!previousVersion.equals(sourceVersion)) {
            throw new IllegalArgumentException(
                    "不支持从 Flowable schema " + sourceVersion + " 升级到 " + targetVersion);
        }
        return previousUpgradeScripts;
    }

    private List<String> allScripts() {
        return java.util.stream.Stream.concat(
                createScripts.stream(), previousUpgradeScripts.stream()).toList();
    }

    private static List<String> list(Properties properties, String key) {
        return Arrays.stream(required(properties, key).split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).toList();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Flowable schema 清单缺少 " + key);
        }
        return value.strip();
    }

    private static InputStream resource(String path) {
        InputStream input = FlowableSchemaManifest.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("Flowable schema 资源不存在: " + path);
        }
        return input;
    }

    private static void resourceExists(String path) {
        try (InputStream ignored = resource(path)) {
            // 只验证资源存在；SQL 执行由数据库矩阵测试负责。
        } catch (IOException ex) {
            throw new IllegalStateException("无法关闭 Flowable schema 资源: " + path, ex);
        }
    }
}
