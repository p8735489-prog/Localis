package com.localaisearch.data.search

import kotlinx.serialization.Serializable

/**
 * Defines the operational modes for the app's search agent.
 *
 * Each mode controls how aggressively the agent queries external search
 * providers and how many results / rounds it consumes.
 */
enum class SearchMode(
    /** Display name shown in the UI (Chinese). */
    val displayName: String,
    /** Short description of the mode's behavior. */
    val description: String
) {
    /** Search is completely disabled. */
    OFF(
        displayName = "关闭搜索",
        description = "不调用任何外部搜索。"
    ),

    /** The AI decides per-query whether a web search is warranted. */
    SMART(
        displayName = "智能搜索",
        description = "AI 根据问题内容自动判断是否联网检索。"
    ),

    /** Every user query triggers a web search. */
    ALWAYS(
        displayName = "始终搜索",
        description = "每个问题都会联网检索最新信息。"
    ),

    /** Aggressive multi-round search with more results per round. */
    DEEP(
        displayName = "深度搜索",
        description = "执行更多轮次、获取更多结果，适合深度调研。"
    )
}

/**
 * Encapsulates user-configurable parameters for the search agent.
 *
 * @property mode The active [SearchMode].
 * @property maxRounds Maximum number of search rounds to execute.
 *           A value of `0` means search is effectively disabled.
 * @property maxResults Maximum number of results to request per round.
 * @property searchLanguage Preferred language for search queries (e.g., "zh-CN", "en").
 * @property enableSourceFiltering When `true`, low-quality or irrelevant sources
 *           are filtered out before being shown to the model.
 * @property minSourceQuality Minimum relevance / quality score (0.0–1.0) a source
 *           must exceed to be included when [enableSourceFiltering] is active.
 */
@Serializable
data class SearchModeConfig(
    val mode: SearchMode = SearchMode.SMART,
    val maxRounds: Int = getEffectiveMaxRounds(SearchMode.SMART),
    val maxResults: Int = getEffectiveMaxResults(SearchMode.SMART),
    val searchLanguage: String = "zh-CN",
    val enableSourceFiltering: Boolean = true,
    val minSourceQuality: Float = 0.3f
) {
    companion object {
        /**
         * The default configuration instance used when the user has not
         * explicitly customized search settings.
         */
        val Default = SearchModeConfig()
    }
}

/**
 * Returns the recommended maximum number of search rounds for a given [SearchMode].
 *
 * - [SearchMode.OFF] → `0` (no searching)
 * - [SearchMode.SMART] → `3` (moderate depth with AI gate-keeping)
 * - [SearchMode.ALWAYS] → `2` (single-pass because every query already searches)
 * - [SearchMode.DEEP] → `5` (multi-round iterative search)
 */
fun getEffectiveMaxRounds(mode: SearchMode): Int = when (mode) {
    SearchMode.OFF -> 0
    SearchMode.SMART -> 3
    SearchMode.ALWAYS -> 2
    SearchMode.DEEP -> 5
}

/**
 * Returns the recommended maximum number of results per round for a given [SearchMode].
 *
 * - [SearchMode.OFF] → `0`
 * - [SearchMode.SMART] → `10`
 * - [SearchMode.ALWAYS] → `15`
 * - [SearchMode.DEEP] → `20` (broader result set for synthesis)
 */
fun getEffectiveMaxResults(mode: SearchMode): Int = when (mode) {
    SearchMode.OFF -> 0
    SearchMode.SMART -> 10
    SearchMode.ALWAYS -> 15
    SearchMode.DEEP -> 20
}
