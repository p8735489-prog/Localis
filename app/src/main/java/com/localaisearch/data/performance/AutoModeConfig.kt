package com.localaisearch.data.performance

import com.localaisearch.data.model.ModelBundle
import com.localaisearch.data.model.ModelCapability
import kotlinx.serialization.Serializable

/**
 * Configuration for the automatic mode selection engine.
 *
 * When [enabled] is `true`, the app automatically picks the best model,
 * decides whether to search the web, and optionally unloads idle models
 * to save battery and RAM.
 *
 * @property enabled Whether auto-mode is active.
 * @property autoUnloadMinutes Minutes of inactivity before an idle model
 *           is automatically unloaded (when auto-unload is enabled).
 */
@Serializable
data class AutoModeConfig(
    val enabled: Boolean = false,
    val autoUnloadMinutes: Int = 5
) {
    companion object {
        /** Default configuration with auto-mode disabled. */
        val Default = AutoModeConfig()
    }
}

/**
 * Engine that automatically classifies user input, selects the most
 * appropriate model, decides whether web search is warranted, and
 * manages model lifecycle (auto-unload) based on idle time.
 *
 * All decisions are made using existing project components:
 * - [ModelRouter] for task classification and model routing
 * - [ModelCapability] for matching models to task requirements
 */
class AutoModeEngine {

    /**
     * Classify the user's input into a [ModelRouter.TaskType].
     *
     * Uses [ModelRouter] heuristics for text-based tasks. Image input is
     * mapped directly to [ModelRouter.TaskType.IMAGE_ANALYSIS] since
     * model availability is not known at this stage.
     *
     * @param userInput The raw text prompt.
     * @param hasImage `true` if the user has attached an image.
     * @return The determined task type.
     */
    fun classifyTask(userInput: String, hasImage: Boolean): ModelRouter.TaskType {
        return if (hasImage) {
            ModelRouter.TaskType.IMAGE_ANALYSIS
        } else {
            // Delegate text classification to ModelRouter with no models loaded.
            // The router will still determine TEXT_CHAT vs WEB_SEARCH based on
            // keyword heuristics.
            val result = ModelRouter.routeTask(
                userInput = userInput,
                imageInput = null,
                availableModels = emptyList(),
                activeModel = null
            )
            result.taskType
        }
    }

    /**
     * Select the best [ModelBundle] from the available list for the given
     * [taskType].
     *
     * Selection rules:
     * - [ModelRouter.TaskType.IMAGE_ANALYSIS] / [ModelRouter.TaskType.MULTIMODAL]
     *   -> first bundle with [ModelCapability.VISION]
     * - [ModelRouter.TaskType.WEB_SEARCH] / [ModelRouter.TaskType.TEXT_CHAT]
     *   -> first bundle with [ModelCapability.TEXT]
     * - Fallback -> first bundle in the list
     *
     * @param taskType The classified task.
     * @param availableBundles All model bundles currently known to the app.
     * @return The best matching bundle, or `null` if the list is empty.
     */
    fun selectModelForTask(
        taskType: ModelRouter.TaskType,
        availableBundles: List<ModelBundle>
    ): ModelBundle? {
        if (availableBundles.isEmpty()) return null

        return when (taskType) {
            ModelRouter.TaskType.IMAGE_ANALYSIS,
            ModelRouter.TaskType.MULTIMODAL -> {
                availableBundles.firstOrNull { bundle ->
                    bundle.capabilities.contains(ModelCapability.VISION)
                } ?: availableBundles.first()
            }

            ModelRouter.TaskType.WEB_SEARCH,
            ModelRouter.TaskType.TEXT_CHAT -> {
                availableBundles.firstOrNull { bundle ->
                    bundle.capabilities.contains(ModelCapability.TEXT)
                } ?: availableBundles.first()
            }
        }
    }

    /**
     * Decide whether a web search should be triggered for the given input.
     *
     * Delegates the core keyword heuristic to [ModelRouter.shouldUseSearchAgent].
     * The caller is expected to gate this further based on the user's
     * [SearchMode] setting (e.g., [SearchMode.OFF] always returns `false`).
     *
     * @param userInput The raw text prompt.
     * @param searchMode The current search mode configuration.
     * @return `true` if web search is recommended for this input.
     */
    fun shouldUseSearch(userInput: String, searchMode: com.localaisearch.data.search.SearchMode): Boolean {
        return when (searchMode) {
            com.localaisearch.data.search.SearchMode.OFF -> false
            com.localaisearch.data.search.SearchMode.ALWAYS,
            com.localaisearch.data.search.SearchMode.DEEP -> true
            com.localaisearch.data.search.SearchMode.SMART ->
                ModelRouter.shouldUseSearchAgent(userInput)
        }
    }

    /**
     * Check whether a model should be unloaded after being idle.
     *
     * @param lastActivityTime Timestamp (ms since epoch) of the last inference
     *        or user interaction with the model.
     * @param idleMinutes Configured idle threshold in minutes.
     * @return `true` if the elapsed idle time exceeds the threshold.
     */
    fun shouldUnloadAfterIdle(lastActivityTime: Long, idleMinutes: Int): Boolean {
        if (idleMinutes <= 0) return false
        val idleMillis = idleMinutes * 60_000L
        return (System.currentTimeMillis() - lastActivityTime) > idleMillis
    }
}
