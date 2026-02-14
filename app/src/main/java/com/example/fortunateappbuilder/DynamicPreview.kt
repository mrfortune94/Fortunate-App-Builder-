package com.example.fortunateappbuilder

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
fun DynamicPreview(jsonStr: String?) {
    if (jsonStr.isNullOrBlank()) {
        Text("No preview available")
        return
    }
    try {
        val root = JSONObject(jsonStr)
        RenderNode(root, Modifier.fillMaxSize().padding(8.dp))
    } catch (e: Exception) {
        Text("Preview parse error: ${e.message}")
    }
}

@Composable
private fun RenderNode(node: JSONObject, modifier: Modifier) {
    when (val type = node.optString("type", "")) {
        "Column" -> Column(modifier = modifier) {
            val children = node.optJSONArray("children") ?: return@Column
            repeat(children.length()) { i ->
                RenderNode(children.getJSONObject(i), Modifier.fillMaxWidth())
            }
        }
        "Text" -> Text(
            text = node.optString("text", ""),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(8.dp)
        )
        "Button" -> Button(onClick = {}, modifier = Modifier.padding(8.dp)) {
            Text(node.optString("text", "Button"))
        }
        else -> Text("Unsupported component: $type", modifier = Modifier.padding(16.dp))
    }
}
