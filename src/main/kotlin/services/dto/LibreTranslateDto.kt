package org.example.services.dto

import kotlinx.serialization.Serializable

@Serializable
data class TranslationResponse(
    val translatedText: String
)