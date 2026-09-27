package com.example.tiktokai

import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

object BackendApiV16 {

    fun health(base:String):Boolean {
        val c=URL(base.trimEnd('/')+"/health").openConnection() as HttpURLConnection
        c.requestMethod="GET"
        c.connectTimeout=8000
        c.readTimeout=8000
        return try {
            val ok=c.responseCode in 200..299
            if(ok) c.inputStream.close() else c.errorStream?.close()
            ok
        } finally { c.disconnect() }
    }

    data class GeneratedContent(
        val title:String,
        val hook:String,
        val description:String,
        val hashtags:List<String>,
        val scenes:List<String>,
        val visualPrompts:List<String> = emptyList()
    )

    fun generateContent(base:String, topic:String, language:String="ar"):GeneratedContent {
        val root=jsonPost(base,"/generate","",JSONObject().put("topic",topic).put("language",language))
        val data=root.optJSONObject("data") ?: root
        fun array(name:String):List<String> {
            val a=data.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { i ->
                val item=a.opt(i)
                if(item is JSONObject) item.optString("text",item.toString()) else item.toString()
            }
        }
        return GeneratedContent(
            data.optString("title"),
            data.optString("hook"),
            data.optString("description"),
            array("hashtags"),
            array("scenes"),
            array("visual_prompts")
        )
    }


    fun generateVisuals(base:String,prompts:List<String>):List<String> {
        val arr=org.json.JSONArray()
        prompts.take(9).forEach { arr.put(it) }
        val root=jsonPost(base,"/visuals/generate","",JSONObject().put("prompts",arr))
        val images=root.optJSONArray("images") ?: return emptyList()
        return (0 until images.length()).map { images.optString(it) }.filter { it.isNotBlank() }
    }

    data class CreatorInfo(
        val username:String, val nickname:String, val privacyOptions:List<String>,
        val commentDisabled:Boolean, val duetDisabled:Boolean, val stitchDisabled:Boolean,
        val maxDurationSec:Int
    )

    private fun jsonPost(base:String, path:String, session:String, body:JSONObject):JSONObject {
        val c=URL(base.trimEnd('/')+path).openConnection() as HttpURLConnection
        c.requestMethod="POST"; c.doOutput=true
        c.connectTimeout=15000; c.readTimeout=60000
        c.setRequestProperty("Content-Type","application/json; charset=UTF-8")
        c.setRequestProperty("X-App-Session",session)
        c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        val text=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)
            .bufferedReader().use{it.readText()}
        if(c.responseCode !in 200..299) throw IOException(text)
        return JSONObject(text)
    }

    fun creatorInfo(base:String,session:String):CreatorInfo {
        val root=jsonPost(base,"/tiktok/creator-info",session,JSONObject().put("session_id",session))
        val d=root.getJSONObject("data")
        val p=d.optJSONArray("privacy_level_options")
        val opts=mutableListOf<String>()
        if(p!=null) for(i in 0 until p.length()) opts.add(p.getString(i))
        return CreatorInfo(
            d.optString("creator_username"), d.optString("creator_nickname"), opts,
            d.optBoolean("comment_disabled"), d.optBoolean("duet_disabled"),
            d.optBoolean("stitch_disabled"), d.optInt("max_video_post_duration_sec")
        )
    }

    fun publishFile(
        base:String, session:String, file:File, title:String, privacy:String,
        allowComment:Boolean, allowDuet:Boolean, allowStitch:Boolean,
        isAigc:Boolean, coverMs:Long
    ):String {
        val boundary="----TikTokAIStudio${System.currentTimeMillis()}"
        val c=URL(base.trimEnd('/')+"/tiktok/publish-file").openConnection() as HttpURLConnection
        c.requestMethod="POST"; c.doOutput=true; c.useCaches=false
        c.connectTimeout=20000; c.readTimeout=240000
        c.setRequestProperty("X-App-Session",session)
        c.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
        DataOutputStream(BufferedOutputStream(c.outputStream)).use { out ->
            fun field(name:String,value:String){
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                out.write(value.toByteArray(Charsets.UTF_8)); out.writeBytes("\r\n")
            }
            field("session_id",session); field("title",title); field("privacy_level",privacy)
            field("allow_comment",allowComment.toString()); field("allow_duet",allowDuet.toString())
            field("allow_stitch",allowStitch.toString()); field("is_aigc",isAigc.toString())
            field("cover_timestamp_ms",coverMs.toString())
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"video\"; filename=\"video.mp4\"\r\n")
            out.writeBytes("Content-Type: video/mp4\r\n\r\n")
            file.inputStream().use{input->input.copyTo(out,1024*1024)}
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        val txt=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)
            .bufferedReader().use{it.readText()}
        if(c.responseCode !in 200..299) throw IOException(txt)
        return JSONObject(txt).getString("publish_id")
    }

    fun status(base:String,session:String,publishId:String):String {
        val root=jsonPost(base,"/tiktok/publish/status",session,
            JSONObject().put("session_id",session).put("publish_id",publishId))
        return root.getJSONObject("data").optString("status","UNKNOWN")
    }
}
