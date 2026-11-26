package com.glia.assistant.tools

import org.json.JSONObject

data class ToolResult(
    val ok: Boolean,
    val action: String?,
    val spoken: String,
    val raw: JSONObject? = null
)

interface GliaTool {
    val name: String
    val description: String
    val inputSchemaJson: String
    fun execute(args: JSONObject): ToolResult
}
