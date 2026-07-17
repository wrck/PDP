package com.pdp.experience.search;

import java.util.List;
import java.util.Map;

/**
 * 平台统一搜索分析器接口（spec.md 第 8 节）。
 *
 * <p>P1 使用平台统一分析器生成 {@link SearchDocument} 和 {@link SearchTermProjection}，
 * 统一 Unicode 规范化、大小写折叠、停用词、字段权重和词项版本。
 *
 * <p><strong>核心契约（SC-033）</strong>：
 * <ol>
 *   <li>同一分析器版本和同一数据集 MUST 产生相同词项、匹配集合和稳定业务排序；</li>
 *   <li>分析器输出由平台契约决定，<strong>不得</strong>依赖 MySQL FULLTEXT 分词或数据库原生分析器；</li>
 *   <li>分析器升级（{@link AnalyzerVersion} MAJOR 变化）MUST 触发后台全量重建，重建完成前不得把
 *       新投影用于约束或流程判断（persistence-design.md 第 7 节）。</li>
 * </ol>
 *
 * <p>实现 MUST 为无状态或线程安全；分析器版本和字段权重配置在构造时确定，运行期不可变。
 */
public interface UnifiedSearchAnalyzer {

    /**
     * 当前分析器版本。
     *
     * @return 分析器版本
     */
    AnalyzerVersion version();

    /**
     * 当前字段权重配置（不可变）。
     *
     * @return 字段权重列表
     */
    List<FieldWeight> fieldWeights();

    /**
     * 查询指定字段的权重。
     *
     * @param fieldKey 字段稳定键
     * @return 字段权重（不存在返回 0.0）
     */
    double weightOf(String fieldKey);

    /**
     * 分析单个字段文本，输出分词结果。
     *
     * <p>步骤：规范化（{@link TextNormalization}）→ 分词 → 去停用词 → 计算词频。
     * 同一分析器版本和同一原始文本 MUST 产生相同结果（SC-033）。
     *
     * @param rawText  原始文本（可为 null 或空，返回空结果）
     * @return 分词结果
     */
    TokenizationResult analyze(String rawText);

    /**
     * 分析多字段文本，输出字段 → 分词结果映射。
     *
     * <p>用于构建 {@link SearchDocument} 和 {@link SearchTermProjection}。
     * 字段权重为 0 的字段 MAY 跳过分词以节省开销，但 MUST 在结果中保留规范化文本。
     *
     * @param fieldTexts 字段稳定键 → 原始文本
     * @return 字段稳定键 → 分词结果
     */
    Map<String, TokenizationResult> analyzeFields(Map<String, String> fieldTexts);

    /**
     * 基于多字段分词结果构建规范化全文（用于 {@link SearchDocument#normalizedText()}）。
     *
     * <p>默认实现按字段权重降序拼接各字段规范化文本；实现可覆盖以支持更复杂的合并策略，
     * 但 MUST 保证同一分析器版本和同一输入下输出一致（SC-033）。
     *
     * @param fieldResults 字段分词结果
     * @return 合并后的规范化文本
     */
    NormalizedText composeNormalizedText(Map<String, TokenizationResult> fieldResults);
}
