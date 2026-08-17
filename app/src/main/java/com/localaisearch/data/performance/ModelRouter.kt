package com.localaisearch.data.performance

import com.localaisearch.data.model.GGUFModel
import com.localaisearch.data.model.ImageInput

/**
 * Routes tasks to the appropriate model based on input type and model capabilities.
 *
 * The router inspects the user input (text and optional image) and the state of
 * available / active models to decide which [TaskType] should be executed and
 * which model should handle it.
 */
object ModelRouter {

    /**
     * Categories of tasks the router can dispatch.
     */
    enum class TaskType {
        TEXT_CHAT,
        IMAGE_ANALYSIS,
        WEB_SEARCH,
        MULTIMODAL
    }

    /**
     * Result of a routing decision.
     *
     * @property selectedModelId The ID of the model chosen to handle the task,
     *           or `null` if no suitable model exists.
     * @property taskType The determined [TaskType].
     * @property requiresFallback `true` when the router had to switch away from
     *           the user's currently active model to satisfy the request.
     * @property fallbackReason Human-readable explanation when a fallback was
     *           required, or `null` when the active model was used directly.
     */
    data class RouteResult(
        val selectedModelId: String?,
        val taskType: TaskType,
        val requiresFallback: Boolean = false,
        val fallbackReason: String? = null
    )

    /**
     * List of keywords that suggest the user is asking for time-sensitive
     * information and would benefit from a web-search agent.
     */
    private val SEARCH_KEYWORDS = listOf(
        "latest", "news", "today", "current", "recent", "now", "update",
        "headline", "breaking", "weather", "stock price", "market"
    )

    /**
     * Vision-related substrings used to heuristically identify vision-capable
     * models from their display name or ID.
     */
    private val VISION_KEYWORDS = listOf(
        "vision", "vlm", "llava", "qwen-vl", "yi-vl", "internvl", "cogvlm",
        "multimodal", "image", "clip"
    )

    /**
     * Determines which model and [TaskType] should handle the user's request.
     *
     * Decision rules (evaluated in order):
     * 1. **Image present & active model is vision-capable** → [TaskType.MULTIMODAL]
     *    with the active model.
     * 2. **Image present & active model is not vision-capable, but another
     *    vision model exists** → [TaskType.MULTIMODAL] with the first vision
     *    model found (marked as fallback).
     * 3. **Image present & no vision model at all** → [TaskType.TEXT_CHAT]
     *    with the active model (or first available) and a fallback reason
     *    explaining that images are unsupported.
     * 4. **Text implies a search need** (contains time-sensitive keywords)
     *    → [TaskType.WEB_SEARCH] with the active model.
     * 5. **Otherwise** → [TaskType.TEXT_CHAT] with the active model.
     *
     * @param userInput The raw text prompt from the user.
     * @param imageInput Optional image attached to the prompt.
     * @param availableModels All models currently known to the app.
     * @param activeModel The model the user has explicitly selected, if any.
     * @return A [RouteResult] describing the chosen model and task type.
     */
    fun routeTask(
        userInput: String,
        imageInput: ImageInput?,
        availableModels: List<GGUFModel>,
        activeModel: GGUFModel?
    ): RouteResult {
        val normalizedInput = userInput.lowercase()

        // --- Image path -------------------------------------------------------
        if (imageInput != null) {
            val activeIsVision = activeModel?.let { isVisionCapable(it) } == true

            if (activeIsVision && activeModel != null) {
                return RouteResult(
                    selectedModelId = activeModel.id,
                    taskType = TaskType.MULTIMODAL,
                    requiresFallback = false,
                    fallbackReason = null
                )
            }

            val visionFallback = availableModels.firstOrNull { isVisionCapable(it) }
            if (visionFallback != null) {
                return RouteResult(
                    selectedModelId = visionFallback.id,
                    taskType = TaskType.MULTIMODAL,
                    requiresFallback = true,
                    fallbackReason = "Active model '${activeModel?.name}' does not support images. " +
                        "Switched to '${visionFallback.name}' for multimodal inference."
                )
            }

            // No vision model available at all
            val selected = activeModel ?: availableModels.firstOrNull()
            return RouteResult(
                selectedModelId = selected?.id,
                taskType = TaskType.TEXT_CHAT,
                requiresFallback = activeModel == null || !activeIsVision,
                fallbackReason = if (selected == null) {
                    "No models available. Image input cannot be processed."
                } else {
                    "No vision-capable model found. '${selected.name}' will handle the request as plain text."
                }
            )
        }

        // --- Web-search path --------------------------------------------------
        if (shouldUseSearchAgent(normalizedInput)) {
            val selected = activeModel ?: availableModels.firstOrNull()
            return RouteResult(
                selectedModelId = selected?.id,
                taskType = TaskType.WEB_SEARCH,
                requiresFallback = false,
                fallbackReason = null
            )
        }

        // --- Default text path ------------------------------------------------
        val selected = activeModel ?: availableModels.firstOrNull()
        return RouteResult(
            selectedModelId = selected?.id,
            taskType = TaskType.TEXT_CHAT,
            requiresFallback = activeModel == null,
            fallbackReason = if (activeModel == null && selected != null) {
                "No active model selected; defaulted to '${selected.name}'."
            } else {
                null
            }
        )
    }

    /**
     * Finds the best fallback [GGUFModel] for a given [taskType].
     *
     * The heuristic prefers:
     * - For [TaskType.MULTIMODAL] or [TaskType.IMAGE_ANALYSIS] → the first
     *   model whose name indicates vision support.
     * - For [TaskType.WEB_SEARCH] or [TaskType.TEXT_CHAT] → the first
     *   available model (no special capability required).
     *
     * @param taskType The kind of task that needs a model.
     * @param availableModels All models currently known to the app.
     * @return The most suitable fallback model, or `null` if the list is empty.
     */
    fun getFallbackModel(
        taskType: TaskType,
        availableModels: List<GGUFModel>
    ): GGUFModel? {
        if (availableModels.isEmpty()) return null

        return when (taskType) {
            TaskType.MULTIMODAL, TaskType.IMAGE_ANALYSIS -> {
                availableModels.firstOrNull { isVisionCapable(it) }
                    ?: availableModels.first()
            }
            TaskType.WEB_SEARCH, TaskType.TEXT_CHAT -> {
                availableModels.first()
            }
        }
    }

    /**
     * Simple heuristic that checks whether the user input contains time-sensitive
     * keywords suggesting a web search would be beneficial.
     *
     * @param userInput The raw or normalized text prompt from the user.
     * @return `true` if the input implies a real-time information need.
     */
    fun shouldUseSearchAgent(userInput: String): Boolean {
        val lower = userInput.lowercase()
        return SEARCH_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }

    /**
     * Checks whether a [GGUFModel] is heuristically considered vision-capable.
     */
    private fun isVisionCapable(model: GGUFModel): Boolean {
        val lower = model.name.lowercase()
        return VISION_KEYWORDS.any { keyword -> lower.contains(keyword) }
    }
}
