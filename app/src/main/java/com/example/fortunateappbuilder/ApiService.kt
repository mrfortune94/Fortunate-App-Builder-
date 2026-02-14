package com.example.fortunateappbuilder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

private val client = OkHttpClient()

suspend fun sendChatToGrok(apiKey: String, messages: List<Message>): String = withContext(Dispatchers.IO) {
    val msgArray = JSONArray()
    messages.forEach {
        val role = if (it.isUser) "user" else "assistant"
        msgArray.put(JSONObject().put("role", role).put("content", it.text))
    }

    val body = JSONObject().apply {
        put("model", "grok-beta")
        put("messages", msgArray)
        put("temperature", 0.7)
    }.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("https://api.x.ai/v1/chat/completions")
        .post(body)
        .header("Authorization", "Bearer $apiKey")
        .build()

    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw Exception("Grok API error: ${resp.code}")
        JSONObject(resp.body!!.string())
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}

data class FlutterProject(
    val projectName: String,
    val pubspecYaml: String,
    val mainDart: String,
    val otherFiles: Map<String, String>
)

suspend fun pushToGitHub(token: String, project: FlutterProject): String? = withContext(Dispatchers.IO) {
    val owner = "YOUR_GITHUB_USERNAME_HERE" // ← CHANGE THIS TO YOUR ACTUAL GITHUB USERNAME
    val repoName = project.projectName.lowercase().replace(" ", "-") + "-flutter"

    try {
        val createBody = JSONObject().put("name", repoName).put("private", false).toString()
        val req = Request.Builder()
            .url("https://api.github.com/user/repos")
            .post(createBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute()
    } catch (_: Exception) {}

    val files = mutableMapOf(
        "pubspec.yaml" to project.pubspecYaml,
        "lib/main.dart" to project.mainDart
    )
    files.putAll(project.otherFiles)

    files.forEach { (path, content) ->
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray())
        val body = JSONObject().apply {
            put("message", "Fortunate App Builder commit: $path")
            put("content", encoded)
            put("branch", "main")
        }.toString()

        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repoName/contents/$path")
            .put(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201) {
                throw Exception("Push failed for $path: ${resp.code}")
            }
        }
    }
    repoName
}

data class WebWrapperProject(
    val appName: String,
    val packageName: String,
    val mainUrl: String,
    val manifestXml: String,
    val mainActivityKt: String,
    val layoutXml: String,
    val stringsXml: String
)

fun generateWebViewWrapperProject(url: String): WebWrapperProject {
    val safeUrl = if (url.startsWith("http")) url else "https://$url"
    val domain = safeUrl.split("://")[1].split("/")[0].replace(".", "-")
    val appName = "WebApp-$domain"
    val pkg = "com.webapp.${domain.replace("-", "")}"

    val manifest = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="$pkg">
            <uses-permission android:name="android.permission.INTERNET" />
            <application
                android:allowBackup="true"
                android:icon="@mipmap/ic_launcher"
                android:label="$appName"
                android:roundIcon="@mipmap/ic_launcher_round"
                android:supportsRtl="true"
                android:theme="@style/Theme.Material3.DayNight.NoActionBar">
                <activity
                    android:name=".MainActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent()

    val mainKt = """
        package $pkg

        import android.os.Bundle
        import android.webkit.WebView
        import android.webkit.WebViewClient
        import androidx.activity.ComponentActivity

        class MainActivity : ComponentActivity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val webView = WebView(this)
                setContentView(webView)
                with(webView.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webView.webViewClient = WebViewClient()
                webView.loadUrl("$safeUrl")
            }

            @Deprecated("Deprecated in Java")
            override fun onBackPressed() {
                val webView = findViewById<WebView>(android.R.id.content)?.getChildAt(0) as? WebView
                if (webView?.canGoBack() == true) webView.goBack() else super.onBackPressed()
            }
        }
    """.trimIndent()

    val layout = """
        <?xml version="1.0" encoding="utf-8"?>
        <WebView xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/webview"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
    """.trimIndent()

    val strings = """
        <resources>
            <string name="app_name">$appName</string>
        </resources>
    """.trimIndent()

    return WebWrapperProject(appName, pkg, safeUrl, manifest, mainKt, layout, strings)
}

suspend fun pushWebWrapperToGitHub(token: String, project: WebWrapperProject): String? = withContext(Dispatchers.IO) {
    val owner = "YOUR_GITHUB_USERNAME_HERE" // ← CHANGE THIS TO YOUR ACTUAL GITHUB USERNAME
    val repoName = project.appName.lowercase().replace(" ", "-") + "-web-apk"

    try {
        val createBody = JSONObject().put("name", repoName).put("private", false).toString()
        val req = Request.Builder()
            .url("https://api.github.com/user/repos")
            .post(createBody.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute()
    } catch (_: Exception) {}

    val files = mapOf(
        "app/src/main/AndroidManifest.xml" to project.manifestXml,
        "app/src/main/java/${project.packageName.replace(".", "/")}/MainActivity.kt" to project.mainActivityKt,
        "app/src/main/res/layout/activity_main.xml" to project.layoutXml,
        "app/src/main/res/values/strings.xml" to project.stringsXml
    )

    files.forEach { (path, content) ->
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray())
        val body = JSONObject().apply {
            put("message", "Fortunate App Builder web wrapper: $path")
            put("content", encoded)
            put("branch", "main")
        }.toString()

        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repoName/contents/$path")
            .put(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 201) throw Exception("Web push failed for $path")
        }
    }
    repoName
}

suspend fun triggerCodemagicBuild(token: String, appId: String, workflowId: String): String? = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        put("appId", appId)
        put("workflowId", workflowId)
        put("branch", "main")
    }.toString().toRequestBody("application/json".toMediaType())

    val req = Request.Builder()
        .url("https://api.codemagic.io/builds")
        .post(body)
        .header("x-auth-token", token)
        .build()

    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw Exception("Codemagic trigger failed: ${resp.code}")
        JSONObject(resp.body!!.string()).optString("buildId", null)
    }
}

fun extractPreviewJson(reply: String): String? {
    val start = reply.indexOf("{", reply.indexOf("preview_json") ?: 0)
    val end = reply.lastIndexOf("}") + 1
    return if (start >= 0 && end > start) reply.substring(start, end) else null
}

fun extractFlutterProject(reply: String): FlutterProject? {
    val start = reply.indexOf("{", reply.indexOf("project") ?: reply.indexOf("{"))
    val end = reply.lastIndexOf("}") + 1
    val jsonStr = if (start >= 0 && end > start) reply.substring(start, end) else return null

    return try {
        val root = JSONObject(jsonStr)
        FlutterProject(
            projectName = root.optString("project_name", "MyApp"),
            pubspecYaml = root.optString("pubspec_yaml", ""),
            mainDart = root.optString("main_dart", ""),
            otherFiles = root.optJSONObject("files")?.let { obj ->
                mutableMapOf<String, String>().apply {
                    obj.keys().forEach { this[it] = obj.getString(it) }
                }
            } ?: emptyMap()
        )
    } catch (e: Exception) { null }
}
