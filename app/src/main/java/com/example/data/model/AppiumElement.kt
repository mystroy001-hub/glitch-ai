package com.example.data.model

data class AppiumElement(
    val name: String,
    val testTag: String,
    val resourceId: String,
    val xpath: String,
    val accessibilityId: String,
    val description: String,
    val actionType: String // e.g. "type_text", "click", "read"
)
