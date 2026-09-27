package com.example.tiktokai

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object ProjectStore {
    private const val PREFS="project_state"
    private const val KEY="last_project"

    data class State(
        val topic:String,
        val content:BackendApiV16.GeneratedContent?,
        val images:List<Uri>
    )

    fun save(context:Context, topic:String, content:BackendApiV16.GeneratedContent?, images:List<Uri>) {
        val root=JSONObject().put("topic",topic)
        if(content!=null) {
            root.put("content",JSONObject()
                .put("title",content.title)
                .put("hook",content.hook)
                .put("description",content.description)
                .put("hashtags",JSONArray(content.hashtags))
                .put("scenes",JSONArray(content.scenes))
                .put("visual_prompts",JSONArray(content.visualPrompts)))
        }
        root.put("images",JSONArray(images.map { it.toString() }))
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(KEY,root.toString()).apply()
    }

    fun load(context:Context):State? {
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,null) ?: return null
        return try {
            val root=JSONObject(raw)
            val data=root.optJSONObject("content")
            fun arr(o:JSONObject,name:String):List<String> {
                val a=o.optJSONArray(name) ?: return emptyList()
                return (0 until a.length()).map { a.optString(it) }
            }
            val content=if(data==null) null else BackendApiV16.GeneratedContent(
                data.optString("title"),data.optString("hook"),data.optString("description"),
                arr(data,"hashtags"),arr(data,"scenes"),arr(data,"visual_prompts"))
            val imgs=root.optJSONArray("images")
            val uris=if(imgs==null) emptyList() else (0 until imgs.length()).mapNotNull {
                runCatching { Uri.parse(imgs.getString(it)) }.getOrNull()
            }
            State(root.optString("topic"),content,uris)
        } catch(_:Exception) { null }
    }

    fun clear(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
