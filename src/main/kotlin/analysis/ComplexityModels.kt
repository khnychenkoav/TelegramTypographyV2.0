package org.example.analysis

import kotlinx.serialization.Serializable

@Serializable
data class ComplexityRulesConfig(
    val complexity_keywords: Map<String, Int>,
    val simplifying_keywords: Map<String, Int>
)