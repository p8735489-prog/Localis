package com.localaisearch.data.model

import kotlinx.serialization.Serializable

/**
 * A citation/reference to a real source used in the final answer.
 * Sources must be real - fabrication of citations is strictly prohibited.
 */
@Serializable
data class Citation(
    val index: Int,
    val title: String,
    val url: String,
    val source: String,
    val snippet: String = "",
    val searchRound: Int = 0
)

/**
 * Creates a [Citation] from a [SearchResult].
 */
fun SearchResult.toCitation(index: Int): Citation {
    return Citation(
        index = index,
        title = title,
        url = url,
        source = source,
        snippet = snippet,
        searchRound = searchRound
    )
}

/**
 * Inline reference marker used within answer text, e.g. [1], [2].
 */
@Serializable
data class CitationMarker(
    val citationIndex: Int,
    val positionInText: Int
)
