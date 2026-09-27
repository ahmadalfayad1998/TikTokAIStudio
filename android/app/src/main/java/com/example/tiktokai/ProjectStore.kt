package com.example.tiktokai

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ProjectStore {
    private const val PREFS="project_state"
    private const val KEY="last_project"
    private const val LIB_PREFS="project_library"
    private const val LIB_KEY="projects"
    private const val MAX_PROJECTS=30

    data class State(
        val topic:String,
        val content:BackendApiV16.GeneratedContent?,
        val images:List<Uri>
    )

    data class SavedProject(
        val id:String,
        val title:String,
        val updatedAt:Long,
        val state:State
    )

    fun save(context:Context, topic:String, content:BackendApiV16.GeneratedContent?, images:List<Uri>) {
        setCurrent(context,State(topic,content,images))
    }

    fun setCurrent(context:Context,state:State) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(KEY,encodeState(state).toString()).apply()
    }

    fun load(context:Context):State? {
        val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY,null) ?: return null
        return runCatching { decodeState(JSONObject(raw)) }.getOrNull()
    }

    fun archiveCurrent(context:Context,state:State):SavedProject {
        val now=System.currentTimeMillis()
        val title=state.content?.title?.trim().takeUnless { it.isNullOrBlank() }
            ?: state.topic.trim().takeIf { it.isNotBlank() }
            ?: "مشروع بدون عنوان"
        val item=SavedProject(UUID.randomUUID().toString(),title,now,state)
        val list=listSaved(context).toMutableList()
        list.add(0,item)
        writeLibrary(context,list.take(MAX_PROJECTS))
        return item
    }

    fun listSaved(context:Context):List<SavedProject> {
        val raw=context.getSharedPreferences(LIB_PREFS,Context.MODE_PRIVATE).getString(LIB_KEY,null)
            ?: return emptyList()
        return runCatching {
            val arr=JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj=arr.optJSONObject(i) ?: return@mapNotNull null
                val stateObj=obj.optJSONObject("state") ?: return@mapNotNull null
                val state=decodeState(stateObj)
                SavedProject(
                    obj.optString("id"),
                    obj.optString("title","مشروع"),
                    obj.optLong("updated_at",0L),
                    state
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun openSaved(context:Context,id:String):Boolean {
        val item=listSaved(context).firstOrNull { it.id==id } ?: return false
        setCurrent(context,item.state)
        return true
    }

    fun deleteSaved(context:Context,id:String) {
        writeLibrary(context,listSaved(context).filterNot { it.id==id })
    }

    fun clear(context:Context) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun writeLibrary(context:Context,list:List<SavedProject>) {
        val arr=JSONArray()
        list.forEach { item ->
            arr.put(JSONObject()
                .put("id",item.id)
                .put("title",item.title)
                .put("updated_at",item.updatedAt)
                .put("state",encodeState(item.state)))
        }
        context.getSharedPreferences(LIB_PREFS,Context.MODE_PRIVATE)
            .edit().putString(LIB_KEY,arr.toString()).apply()
    }

    private fun encodeState(state:State):JSONObject {
        val root=JSONObject().put("topic",state.topic)
        val content=state.content
        if(content!=null) {
            root.put("content",JSONObject()
                .put("title",content.title)
                .put("hook",content.hook)
                .put("description",content.description)
                .put("hashtags",JSONArray(content.hashtags))
                .put("scenes",JSONArray(content.scenes))
                .put("visual_prompts",JSONArray(content.visualPrompts)))
        }
        root.put("images",JSONArray(state.images.map { it.toString() }))
        return root
    }

    private fun decodeState(root:JSONObject):State {
        val data=root.optJSONObject("content")
        fun arr(o:JSONObject,name:String):List<String> {
            val a=o.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }
        }
        val content=if(data==null) null else BackendApiV16.GeneratedContent(
            data.optString("title"),
            data.optString("hook"),
            data.optString("description"),
            arr(data,"hashtags"),
            arr(data,"scenes"),
            arr(data,"visual_prompts")
        )
        val imgs=root.optJSONArray("images")
        val uris=if(imgs==null) emptyList() else (0 until imgs.length()).mapNotNull {
            runCatching { Uri.parse(imgs.getString(it)) }.getOrNull()
        }
        return State(root.optString("topic"),content,uris)
    }
}
