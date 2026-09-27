package com.example.tiktokai

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.util.Base64
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
    private lateinit var autoImagesButton: Button
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
        autoImagesButton=findViewById(R.id.autoImagesBtn)
        autoImagesButton.setOnClickListener { generateAutomaticImages() }
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
                runOnUiThread { showTikTokExportDialog(info,file,content,session) }
            } catch(ex:Exception) {
                runOnUiThread { status.text="تعذر قراءة Creator Info: "+(ex.message ?: "خطأ") }
            }
        }
    }

    private fun showTikTokExportDialog(
        info:BackendApiV16.CreatorInfo,
        file:File,
        content:BackendApiV16.GeneratedContent,
        session:String
    ) {
        if(info.privacyOptions.isEmpty()) {
            status.text="TikTok لم يرجع خيارات خصوصية متاحة"
            return
        }
        val pad=(16*resources.displayMetrics.density).toInt()
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(pad,pad/2,pad,pad/2)
        }
        val account=(info.nickname.ifBlank { info.username }).ifBlank { "الحساب المرتبط" }
        root.addView(TextView(this).apply { text="الحساب: $account\nالفيديو مولّد بالذكاء الاصطناعي (AIGC)" })

        val caption=EditText(this).apply {
            hint="وصف الفيديو"
            minLines=3
            maxLines=6
            setText(buildString {
                if(content.title.isNotBlank()) append(content.title).append("\n")
                if(content.description.isNotBlank()) append(content.description).append("\n")
                if(content.hashtags.isNotEmpty()) append(content.hashtags.joinToString(" "))
            }.take(2100))
        }
        root.addView(caption)

        val privacyItems=listOf("اختر الخصوصية…")+info.privacyOptions
        val privacy=Spinner(this).apply {
            adapter=ArrayAdapter(this@EasyMainActivity,android.R.layout.simple_spinner_dropdown_item,privacyItems)
        }
        root.addView(privacy)

        val comments=CheckBox(this).apply {
            text="السماح بالتعليقات"
            isEnabled=!info.commentDisabled
            isChecked=false
        }
        val duet=CheckBox(this).apply {
            text="السماح بـ Duet"
            isEnabled=!info.duetDisabled
            isChecked=false
        }
        val stitch=CheckBox(this).apply {
            text="السماح بـ Stitch"
            isEnabled=!info.stitchDisabled
            isChecked=false
        }
        root.addView(comments);root.addView(duet);root.addView(stitch)

        val dialog=AlertDialog.Builder(this)
            .setTitle("إعداد النشر على TikTok")
            .setView(root)
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("نشر",null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(privacy.selectedItemPosition==0) {
                    Toast.makeText(this,"اختر خصوصية النشر أولاً",Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val chosen=info.privacyOptions[privacy.selectedItemPosition-1]
                val text=caption.text.toString().take(2100)
                dialog.dismiss()
                startTikTokPublish(
                    file,content,session,chosen,info,
                    text,comments.isChecked,duet.isChecked,stitch.isChecked
                )
            }
        }
        dialog.show()
    }

    private fun startTikTokPublish(
        file:File,
        content:BackendApiV16.GeneratedContent,
        session:String,
        privacy:String,
        info:BackendApiV16.CreatorInfo,
        caption:String,
        allowComment:Boolean,
        allowDuet:Boolean,
        allowStitch:Boolean
    ) {
        status.text="جاري رفع الفيديو إلى TikTok…"
        thread {
            try {
                val publishId=BackendApiV16.publishFile(
                    backendUrl(),session,file,caption,privacy,
                    allowComment=allowComment && !info.commentDisabled,
                    allowDuet=allowDuet && !info.duetDisabled,
                    allowStitch=allowStitch && !info.stitchDisabled,
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


    private fun generateAutomaticImages() {
        val content=lastContent
        if(content==null) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        val base=backendUrl()
        if(base.contains("10.0.2.2")) {
            status.text="الصور التلقائية تحتاج Backend متصل ومزود صور مفعّل"
            return
        }
        val prompts=content.visualPrompts.ifEmpty {
            (listOf(content.hook)+content.scenes)
                .filter { it.isNotBlank() }
                .map { "Cinematic vertical 9:16 scene, no text, no watermark: $it" }
        }
        if(prompts.isEmpty()) {
            status.text="لا توجد أوصاف بصرية لتوليد الصور"
            return
        }
        autoImagesButton.isEnabled=false
        status.text="جاري توليد صور المشاهد…"
        thread {
            try {
                val encoded=BackendApiV16.generateVisuals(base,prompts)
                if(encoded.isEmpty()) throw IllegalStateException("الخادم لم يرجع صورًا")
                val dir=File(getExternalFilesDir("visuals"),"generated").apply { mkdirs() }
                val newUris=mutableListOf<Uri>()
                encoded.forEachIndexed { index,raw ->
                    val clean=if(raw.startsWith("data:") && raw.contains(",")) raw.substringAfter(",") else raw
                    val bytes=Base64.decode(clean,Base64.DEFAULT)
                    val file=File(dir,"scene_"+(index+1)+".png")
                    file.writeBytes(bytes)
                    newUris.add(Uri.fromFile(file))
                }
                runOnUiThread {
                    selectedImages.clear()
                    selectedImages.addAll(newUris)
                    imagesStatus.text="تم توليد "+newUris.size+" صورة تلقائيًا ✓"
                    status.text="الصور جاهزة ✓ — يمكنك إنشاء الفيديو"
                    autoImagesButton.isEnabled=true
                    saveProject()
                }
            } catch(ex:Exception) {
                runOnUiThread {
                    autoImagesButton.isEnabled=true
                    status.text="تعذر توليد الصور: "+(ex.message ?: "تحقق من مزود الصور في Backend")
                }
            }
        }
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
                    SimpleVideoRenderer.render(
                        this,
                        (listOf(content.hook)+content.scenes).filter { it.isNotBlank() },
                        duration+500L,
                        selectedImages
                    )
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
        ),
        visualPrompts=listOf(
            "Cinematic vertical image illustrating the hook for: $idea, realistic, dramatic lighting, no text",
            "Cinematic vertical scene about: $idea, people reacting, realistic, no text",
            "Cinematic vertical scene showing immediate consequences, realistic, no text",
            "Cinematic vertical scene showing daily-life impact, realistic, no text",
            "Cinematic vertical scene showing surprising consequences, realistic, no text",
            "Cinematic vertical closing scene inviting reflection, realistic, no text"
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
