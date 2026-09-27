package com.example.tiktokai

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.core.content.FileProvider
import java.io.File
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import kotlin.concurrent.thread

class EasyMainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var topic: EditText
    private lateinit var result: TextView
    private var lastVideo: File? = null
    private var lastContent: BackendApiV16.GeneratedContent? = null
    private lateinit var renderButton: Button
    private lateinit var imagesStatus: TextView
    private val selectedImages=mutableListOf<Uri>()
    private val imageRequestCode=701

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status)
        topic=findViewById(R.id.topic)
        result=findViewById(R.id.result)
        imagesStatus=findViewById(R.id.imagesStatus)

        findViewById<Button>(R.id.generateBtn).setOnClickListener { generate() }
        findViewById<Button>(R.id.pickImagesBtn).setOnClickListener { pickImages() }
        renderButton=findViewById(R.id.renderBtn)
        renderButton.setOnClickListener { renderVideo() }
        findViewById<Button>(R.id.previewBtn).setOnClickListener { previewVideo() }
        findViewById<Button>(R.id.shareBtn).setOnClickListener { shareVideo() }
        findViewById<Button>(R.id.newProjectBtn).setOnClickListener { newProject() }
        findViewById<Button>(R.id.connectBtn).setOnClickListener { connectTikTok() }
        findViewById<Button>(R.id.publishBtn).setOnClickListener { publishTikTok() }
        findViewById<Button>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivityV19::class.java))
        }
        restoreProject()
        handleOAuthIntent(intent)
    }

    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }



    private fun backendUrl():String =
        getSharedPreferences("app_settings",MODE_PRIVATE)
            .getString("backend","http://10.0.2.2:8765")!!.trimEnd('/')

    private fun connectTikTok() {
        val base=backendUrl()
        if(base.contains("10.0.2.2")) {
            status.text="ربط TikTok يحتاج Backend عام HTTPS وإعداد TikTok Developer"
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(base+"/oauth/tiktok/start")))
            status.text="تم فتح تسجيل TikTok…"
        } catch(ex:Exception) {
            status.text="تعذر فتح صفحة تسجيل TikTok"
        }
    }

    private fun handleOAuthIntent(intent:Intent?) {
        val uri=intent?.data ?: return
        if(uri.scheme!="tiktokai" || uri.host!="oauth") return
        val error=uri.getQueryParameter("error")
        if(!error.isNullOrBlank()) {
            status.text="فشل ربط TikTok: $error"
            return
        }
        val session=uri.getQueryParameter("session_id")
        if(!session.isNullOrBlank()) {
            getSharedPreferences("app_settings",MODE_PRIVATE)
                .edit().putString("tiktok_session",session).apply()
            status.text="تم ربط TikTok ✓"
        }
    }

    private fun publishTikTok() {
        val prefs=getSharedPreferences("app_settings",MODE_PRIVATE)
        val session=prefs.getString("tiktok_session","").orEmpty()
        if(session.isBlank()) {
            status.text="اربط حساب TikTok أولاً"
            return
        }
        val file=lastVideo ?: File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
        if(!file.exists()) {
            status.text="أنشئ الفيديو أولاً"
            return
        }
        val content=lastContent ?: run {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        status.text="جاري قراءة إعدادات حساب TikTok…"
        thread {
            try {
                val info=BackendApiV16.creatorInfo(backendUrl(),session)
                runOnUiThread { showTikTokPrivacyDialog(info,file,content,session) }
            } catch(ex:Exception) {
                runOnUiThread { status.text="تعذر قراءة Creator Info: "+(ex.message ?: "خطأ") }
            }
        }
    }

    private fun showTikTokPrivacyDialog(
        info:BackendApiV16.CreatorInfo,
        file:File,
        content:BackendApiV16.GeneratedContent,
        session:String
    ) {
        if(info.privacyOptions.isEmpty()) {
            status.text="TikTok لم يرجع خيارات خصوصية متاحة"
            return
        }
        val labels=info.privacyOptions.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("اختر خصوصية النشر")
            .setItems(labels) { _,which ->
                val privacy=info.privacyOptions[which]
                val account=(info.nickname.ifBlank { info.username }).ifBlank { "الحساب المرتبط" }
                AlertDialog.Builder(this)
                    .setTitle("تأكيد النشر")
                    .setMessage("سيتم إرسال الفيديو إلى $account عبر TikTok.\nالخصوصية: $privacy\nالفيديو مولّد بالذكاء الاصطناعي وسيُرسل مع وسم AIGC.")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("نشر الآن") { _,_ ->
                        startTikTokPublish(file,content,session,privacy,info)
                    }.show()
            }
            .setNegativeButton("إلغاء",null)
            .show()
    }

    private fun startTikTokPublish(
        file:File,
        content:BackendApiV16.GeneratedContent,
        session:String,
        privacy:String,
        info:BackendApiV16.CreatorInfo
    ) {
        val caption=buildString {
            if(content.title.isNotBlank()) append(content.title).append("\n")
            if(content.description.isNotBlank()) append(content.description).append("\n")
            if(content.hashtags.isNotEmpty()) append(content.hashtags.joinToString(" "))
        }.take(2100)
        status.text="جاري رفع الفيديو إلى TikTok…"
        thread {
            try {
                val publishId=BackendApiV16.publishFile(
                    backendUrl(),session,file,caption,privacy,
                    allowComment=!info.commentDisabled,
                    allowDuet=!info.duetDisabled,
                    allowStitch=!info.stitchDisabled,
                    isAigc=true,
                    coverMs=1000L
                )
                pollTikTokStatus(session,publishId)
            } catch(ex:Exception) {
                runOnUiThread { status.text="فشل إرسال الفيديو إلى TikTok: "+(ex.message ?: "خطأ") }
            }
        }
    }

    private fun pollTikTokStatus(session:String,publishId:String) {
        repeat(30) { attempt ->
            try {
                val s=BackendApiV16.status(backendUrl(),session,publishId)
                runOnUiThread { status.text="TikTok: $s" }
                if(s=="PUBLISH_COMPLETE") {
                    runOnUiThread { status.text="تم النشر على TikTok ✓" }
                    return
                }
                if(s=="FAILED") {
                    runOnUiThread { status.text="TikTok أبلغ عن فشل النشر" }
                    return
                }
            } catch(_:Exception) {
                if(attempt==29) runOnUiThread { status.text="تم الرفع، لكن تعذر تأكيد حالة النشر" }
            }
            Thread.sleep(3000)
        }
        runOnUiThread { status.text="تم إرسال الفيديو وTikTok ما زال يعالجه" }
    }

    private fun pickImages() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type="image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent,imageRequestCode)
    }

    @Deprecated("Deprecated in Android API, kept for broad device compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=imageRequestCode || resultCode!=RESULT_OK || data==null) return
        selectedImages.clear()
        val flags=data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        fun addUri(uri:Uri?) {
            if(uri==null || selectedImages.size>=10) return
            try { contentResolver.takePersistableUriPermission(uri,flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}
            selectedImages.add(uri)
        }
        val clip=data.clipData
        if(clip!=null) {
            for(i in 0 until clip.itemCount) addUri(clip.getItemAt(i).uri)
        } else addUri(data.data)
        imagesStatus.text=if(selectedImages.isEmpty()) "لم يتم اختيار صور" else "تم اختيار "+selectedImages.size+" صورة ✓"
        saveProject()
    }


    private fun saveProject() {
        ProjectStore.save(this,topic.text.toString(),lastContent,selectedImages)
    }

    private fun restoreProject() {
        val state=ProjectStore.load(this) ?: return
        topic.setText(state.topic)
        lastContent=state.content
        selectedImages.clear()
        selectedImages.addAll(state.images)
        if(state.content!=null) result.text=displayContent(state.content)
        imagesStatus.text=if(selectedImages.isEmpty()) "لم يتم اختيار صور بعد" else "تم استرجاع "+selectedImages.size+" صورة ✓"
        status.text="تم استرجاع آخر مشروع ✓"
        val existing=File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
        if(existing.exists()) lastVideo=existing
    }

    private fun newProject() {
        lastContent=null
        lastVideo=null
        selectedImages.clear()
        topic.setText("")
        result.text=""
        imagesStatus.text="لم يتم اختيار صور بعد"
        status.text="مشروع جديد"
        ProjectStore.clear(this)
    }

    private fun shareVideo() {
        val file=lastVideo ?: File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
        if(!file.exists()) {
            status.text="أنشئ الفيديو أولاً"
            return
        }
        val uri=FileProvider.getUriForFile(this,packageName+".provider",file)
        val share=Intent(Intent.ACTION_SEND).apply {
            type="video/mp4"
            putExtra(Intent.EXTRA_STREAM,uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share,"مشاركة الفيديو"))
    }

    private fun renderVideo() {
        val content=lastContent
        if(content==null) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        val narration=(listOf(content.hook)+content.scenes).filter { it.isNotBlank() }.joinToString(". ")
        if(narration.isBlank()) {
            status.text="المحتوى لا يحتوي نصًا صالحًا للصوت"
            return
        }
        status.text="جاري إنشاء الصوت العربي والفيديو العمودي…"
        renderButton.isEnabled=false
        thread {
            try {
                runOnUiThread { status.text="1/4 جاري إنشاء الصوت العربي…" }
                val voice=ArabicTtsEngine.synthesize(this, narration)
                runOnUiThread { status.text="2/4 تم الصوت ✓ — جاري قياس المدة…" }
                val mmr=MediaMetadataRetriever()
                mmr.setDataSource(voice.absolutePath)
                val duration=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 8000L
                mmr.release()
                runOnUiThread { status.text="3/4 جاري إنشاء الفيديو العمودي…" }
                val silent=try {
                    SimpleVideoRenderer.render(this, content.scenes.ifEmpty { listOf(content.hook) }, duration+500L, selectedImages)
                } catch(ex:Exception) {
                    throw IllegalStateException("المرحلة 3/4: "+(ex.message ?: "فشل ترميز الفيديو"), ex)
                }
                runOnUiThread { status.text="4/4 جاري تحويل الصوت إلى AAC ودمجه…" }
                val finalFile=File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
                val file=try {
                    AudioVideoMuxer.mux(silent,voice,finalFile)
                } catch(ex:Exception) {
                    throw IllegalStateException("المرحلة 4/4: "+(ex.message ?: "فشل دمج الصوت والفيديو"), ex)
                }
                lastVideo=file
                runOnUiThread {
                    status.text="تم إنشاء الفيديو ✓ — يمكنك المعاينة أو المشاركة"
                    renderButton.isEnabled=true
                    saveProject()
                }
            } catch(ex:Exception) {
                runOnUiThread { status.text="فشل إنشاء الفيديو: "+(ex.message ?: "خطأ غير معروف"); renderButton.isEnabled=true }
            }
        }
    }

    private fun previewVideo() {
        val file=lastVideo ?: File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
        if(!file.exists()) {
            status.text="لا يوجد فيديو بعد — أنشئ الفيديو أولاً"
            return
        }
        val uri: Uri=FileProvider.getUriForFile(this, packageName+".provider", file)
        val intent=Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri,"video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch(ex:Exception) {
            status.text="لا يوجد مشغل فيديو متاح على الجهاز"
        }
    }

    private fun displayContent(c:BackendApiV16.GeneratedContent):String = buildString {
        if(c.title.isNotBlank()) append("العنوان: ").append(c.title).append("\n\n")
        if(c.hook.isNotBlank()) append("الافتتاحية: ").append(c.hook).append("\n\n")
        if(c.scenes.isNotEmpty()) {
            append("المشاهد:\n")
            c.scenes.forEachIndexed { i,s -> append(i+1).append(". ").append(s).append("\n") }
            append("\n")
        }
        if(c.description.isNotBlank()) append("الوصف: ").append(c.description).append("\n\n")
        if(c.hashtags.isNotEmpty()) append(c.hashtags.joinToString(" "))
    }

    private fun offlineDemoContent(idea:String)=BackendApiV16.GeneratedContent(
        title=idea,
        hook="تخيل أن هذا حدث فجأة… ماذا ستكون أول ردة فعل لك؟",
        description="فيديو تجريبي تم إنشاؤه محليًا لاختبار الصوت والمشاهد قبل اتصال خادم الذكاء الاصطناعي.",
        hashtags=listOf("#ذكاء_اصطناعي","#معلومات","#TikTok"),
        scenes=listOf(
            "نبدأ بالسؤال: $idea",
            "في اللحظات الأولى سيحاول الجميع فهم ما الذي يحدث.",
            "بعدها تبدأ التأثيرات بالظهور في حياتنا اليومية بشكل واضح.",
            "بعض النتائج ستكون متوقعة، لكن نتائج أخرى قد تفاجئنا.",
            "والآن دورك: ماذا تتوقع أن يحدث؟"
        )
    )

    private fun generate() {
        val idea=topic.text.toString().trim()
        if(idea.isEmpty()) {
            status.text="اكتب فكرة الفيديو أولاً"
            return
        }
        val prefs=getSharedPreferences("app_settings", MODE_PRIVATE)
        val backend=prefs.getString("backend","http://10.0.2.2:8765") ?: return
        status.text="جاري إنشاء المحتوى بالذكاء الاصطناعي…"
        result.text=""
        if(backend.contains("10.0.2.2")) {
            val c=offlineDemoContent(idea)
            lastContent=c
            result.text=displayContent(c)
            status.text="وضع الاختبار المحلي ✓"
            saveProject()
            return
        }
        thread {
            try {
                val c=BackendApiV16.generateContent(backend,idea)
                lastContent=c
                val text=displayContent(c)
                runOnUiThread {
                    result.text=if(text.isBlank()) "وصل رد من الخادم لكنه لا يحتوي محتوى صالحًا." else text
                    status.text="تم إنشاء المحتوى ✓"
                    saveProject()
                }
            } catch(ex:Exception) {
                val c=offlineDemoContent(idea)
                lastContent=c
                val text=displayContent(c)
                runOnUiThread {
                    result.text=text
                    status.text="وضع الاختبار المحلي ✓ — الخادم غير متصل"
                    saveProject()
                }
            }
        }
    }
}
