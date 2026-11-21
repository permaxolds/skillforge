package com.skillforge.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Api {
    /** Calls YOUR backend (never the AI provider directly). The AI key lives only on the server. */
    suspend fun chat(baseUrl: String, token: String, messages: List<Pair<String, String>>, context: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = (URL(baseUrl.trim().trimEnd('/') + "/chat").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 90000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
            }
            val body = JSONObject()
                .put("messages", JSONArray(messages.map { JSONObject().put("role", it.first).put("content", it.second) }))
                .put("context", context)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val txt = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) throw IOException(if (code == 401) "Wrong app token (Settings → Backend token)." else "Server error $code: ${txt.take(200)}")
            JSONObject(txt)
        }
}
