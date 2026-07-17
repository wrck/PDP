package com.pdp.experience.search;

import java.util.Set;

/**
 * 停用词词典接口。
 *
 * <p>对应 spec.md 第 8 节"停用词"。停用词在分析器分词后被过滤，不进入词项投影。
 * 停用词集合 MUST 数据库无关且与分析器版本绑定：词典扩展时递增 {@link AnalyzerVersion#minor()}，
 * 旧版本投影不重建时仍使用旧词典结果（保证 SC-033 一致性）。
 *
 * <p>实现 MUST 为无状态、线程安全；词典内容应在分析器构造时确定，运行期不可变。
 */
public interface StopWordDictionary {

    /**
     * 判断词项是否为停用词。
     *
     * @param term 规范化后的词项（非 null）
     * @return true 表示该词项应被过滤
     */
    boolean isStopWord(String term);

    /**
     * 当前词典的停用词集合快照（不可变）。
     * <p>用于分析器版本一致性和审计；返回集合 MUST 不可变。
     *
     * @return 停用词集合
     */
    Set<String> stopWords();
}
