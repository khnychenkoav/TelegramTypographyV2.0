package org.example.analysis

import kotlinx.serialization.Serializable

@Serializable
data class CannedResponseConfig(
    val id: String,
    val answer: String,
    val threshold: Int,
    val keywords: Map<String, Int>,
    val negative_keywords: List<String> = emptyList()
)