package com.example.fortunateappbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BuilderScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen() {
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var input by remember { mutableStateOf("") }
    var xaiKey by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var codemagicToken by remember { mutableStateOf("") }
    var codemagicAppId by remember { mutableStateOf("") }
    var codemagicWorkflowId by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var previewJson by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fortunate App Builder") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(xaiKey, { xaiKey = it }, label = { Text("xAI API Key") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(githubToken, { githubToken = it }, label = { Text("GitHub PAT (repo scope)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(codemagicToken, { codemagicToken = it }, label = { Text("Codemagic API Token") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(codemagicAppId, { codemagicAppId = it }, label = { Text("Codemagic App ID") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(codemagicWorkflowId, { codemagicWorkflowId = it }, label = { Text("Codemagic Workflow ID") }, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Text(status, color = MaterialTheme.colorScheme.primary)

            if (previewJson != null) {
                Text("Live Preview:", style = MaterialTheme.typography.titleMedium)
                DynamicPreview(previewJson)
                Spacer(Modifier.height(16.dp))
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages) { msg ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            if (msg.isUser) "You: ${msg.text}" else "Grok: ${msg.text}",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe app / 'preview' / 'build apk' / 'web to apk https://...'") }
                )
                Button(onClick = {
                    if (input.isBlank() || xaiKey.isBlank()) return@Button

                    val userMsg = Message(input.trim(), true)
                    messages = messages + userMsg
                    val command = input.trim().lowercase()
                    val original = input.trim()
                    input = ""

                    scope.launch {
                        try {
                            val reply = sendChatToGrok(xaiKey, messages)
                            messages = messages + Message(reply, false)

                            if (command.contains("preview")) {
                                previewJson = extractPreviewJson(reply)
                            }

                            if (command.contains("build apk") || command.contains("generate apk") || command.contains("build now")) {
                                status = "Generating Flutter project..."
                                val project = extractFlutterProject(reply)
                                if (project != null && githubToken.isNotBlank()) {
                                    status = "Pushing to GitHub..."
                                    val repoName = pushToGitHub(githubToken, project)
                                    if (repoName != null && codemagicToken.isNotBlank() && codemagicAppId.isNotBlank() && codemagicWorkflowId.isNotBlank()) {
                                        status = "Triggering native APK build..."
                                        val buildId = triggerCodemagicBuild(codemagicToken, codemagicAppId, codemagicWorkflowId)
                                        status = buildId?.let { "Build started (ID: $it)" } ?: "Build trigger failed"
                                    } else {
                                        status = "Push or Codemagic config failed"
                                    }
                                } else {
                                    status = "No project or GitHub token missing"
                                }
                            }

                            if (command.contains("web to apk") || command.contains("convert url") || command.contains("url to apk")) {
                                val url = extractUrlFromCommand(original) ?: "https://example.com"
                                status = "Creating WebView wrapper for $url..."
                                val webProj = generateWebViewWrapperProject(url)
                                if (githubToken.isNotBlank()) {
                                    status = "Pushing WebView project..."
                                    val repoName = pushWebWrapperToGitHub(githubToken, webProj)
                                    if (repoName != null && codemagicToken.isNotBlank() && codemagicAppId.isNotBlank() && codemagicWorkflowId.isNotBlank()) {
                                        status = "Triggering Web→APK build..."
                                        val buildId = triggerCodemagicBuild(codemagicToken, codemagicAppId, codemagicWorkflowId)
                                        status = buildId?.let { "Web APK build started (ID: $it)" } ?: "Build failed"
                                    } else {
                                        status = "Web push or Codemagic config failed"
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            messages = messages + Message("Error: ${e.message}", false)
                            status = "Operation failed"
                        }
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

data class Message(val text: String, val isUser: Boolean)

fun extractUrlFromCommand(input: String): String? {
    return input.split(" ").firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
}
