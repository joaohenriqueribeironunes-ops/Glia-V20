package com.glia.assistant.llm

import com.glia.assistant.tools.ToolCall
import org.json.JSONObject

object ToolCallParser {
    fun parse(text: String): ToolCall? {
        val cleaned = stripFences(text.trim())
        if (!cleaned.startsWith("{")) return null
        val root = try {
            JSONObject(cleaned)
        } catch (_: Exception) {
            return null
        }
        if (root.optString("tool").isBlank()) return null
        if (!root.has("tool") || !root.has("args")) return null
        val keys = root.length()
        if (keys != 2) return null
        val args = root.optJSONObject("args") ?: return null
        val validated = validate(root.optString("tool"), args) ?: return null
        return ToolCall(root.optString("tool"), validated)
    }

    fun validate(tool: String, args: JSONObject): JSONObject? {
        val a = JSONObject()
        when (tool) {
            "hora_atual" -> {
                if (args.length() != 0) return null
                a.put("tool", tool)
            }
            "data_atual" -> {
                if (args.length() != 0) return null
                a.put("tool", tool)
            }
            "dia_da_semana" -> {
                if (!args.has("day") || !args.has("month") || !args.has("year")) return null
                if (args.optInt("day") < 1 || args.optInt("day") > 31) return null
                if (args.optInt("month") < 1 || args.optInt("month") > 12) return null
                if (args.optInt("year") < 1900 || args.optInt("year") > 2100) return null
                a.put("tool", tool)
                a.put("day", args.optInt("day"))
                a.put("month", args.optInt("month"))
                a.put("year", args.optInt("year"))
            }
            "lanterna", "bluetooth", "wifi" -> {
                val action = args.optString("action")
                if (action != "on" && action != "off" && action != "toggle" && action != "status") return null
                a.put("tool", tool).put("action", action)
            }
            else -> return null
        }
        return a
    }

    private fun stripFences(text: String): String {
        val t = text.trim()
        if (t.startsWith("```json")) {
            val start = t.indexOf('\n')
            val end = t.lastIndexOf("```")
            if (end > start) return t.substring(start + 1, end).trim()
        }
        return t
    }
}
