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
    private lateinit var scenePreview: ImageView
    private lateinit var autoImagesButton: Button
    private val selectedImages=mutableListOf<Uri>()
    private val imageRequestCode=701
    private val projectsRequestCode=702

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status=findViewById(R.id.status)
        topic=findViewById(R.id.topic)
        result=findViewById(R.id.result)
        imagesStatus=findViewById(R.id.imagesStatus)
        scenePreview=findViewById(R.id.scenePreview)

        findViewById<Button>(R.id.generateBtn).setOnClickListener { generate() }
        findViewById<Button>(R.id.editContentBtn).setOnClickListener { editContent() }
        findViewById<Button>(R.id.pickImagesBtn).setOnClickListener { pickImages() }
        findViewById<Button>(R.id.localScenesBtn).setOnClickListener { generateLocalScenes() }
        autoImagesButton=findViewById(R.id.autoImagesBtn)
        autoImagesButton.setOnClickListener { generateAutomaticImages() }
        renderButton=findViewById(R.id.renderBtn)
        renderButton.setOnClickListener { renderVideo() }
        findViewById<Button>(R.id.previewBtn).setOnClickListener { previewVideo() }
        findViewById<Button>(R.id.shareBtn).setOnClickListener { shareVideo() }
        findViewById<Button>(R.id.saveCopyBtn).setOnClickListener { saveProjectCopy() }
        findViewById<Button>(R.id.projectsBtn).setOnClickListener { openProjects() }
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



    private fun backendUrl():String = DeviceEnvironment.backendUrl(this)

    private fun connectTikTok() {
        val base=backendUrl()
        if(base.isBlank()) {
            status.text="ربط TikTok يحتاج Backend عام HTTPS وإعداد TikTok Developer"
            return
        }
        if(!base.startsWith("https://")) {
            status.text="لربط TikTok استخدم Backend عام عبر HTTPS"
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
        status.text="جاري التحقق من الفيديو ورفعه إلى TikTok…"
        thread {
            try {
                val mmr=MediaMetadataRetriever()
                mmr.setDataSource(file.absolutePath)
                val durationMs=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                mmr.release()
                val durationSec=((durationMs+999L)/1000L).toInt()
                if(info.maxDurationSec>0 && durationSec>info.maxDurationSec) {
                    runOnUiThread {
                        status.text="مدة الفيديو "+durationSec+" ثانية وتتجاوز حد TikTok للحساب ("+info.maxDurationSec+" ثانية)"
                    }
                    return@thread
                }
                val publishId=BackendApiV16.publishFile(
                    backendUrl(),session,file,caption,privacy,
                    allowComment=allowComment && !info.commentDisabled,
                    allowDuet=allowDuet && !info.duetDisabled,
                    allowStitch=allowStitch && !info.stitchDisabled,
                    isAigc=true,
                    coverMs=1000L,
                    durationSec=durationSec
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



    private fun editContent() {
        val current=lastContent
        if(current==null) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val p=(16*resources.displayMetrics.density).toInt()
            setPadding(p,p/2,p,p/2)
        }
        fun field(hintText:String,value:String,min:Int=1):EditText {
            return EditText(this).apply {
                hint=hintText
                setText(value)
                minLines=min
                maxLines=if(min>1) 8 else 3
            }
        }
        val titleField=field("العنوان",current.title)
        val hookField=field("الافتتاحية",current.hook,2)
        val scenesField=field("المشاهد — كل مشهد في سطر",current.scenes.joinToString("\n"),5)
        val descField=field("الوصف",current.description,3)
        val tagsField=field("الهاشتاغات",current.hashtags.joinToString(" "))
        root.addView(titleField)
        root.addView(hookField)
        root.addView(scenesField)
        root.addView(descField)
        root.addView(tagsField)
        scroll.addView(root)

        val dialog=AlertDialog.Builder(this)
            .setTitle("تعديل المحتوى")
            .setView(scroll)
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("حفظ",null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val scenes=scenesField.text.toString().lines()
                    .map { it.trim() }.filter { it.isNotBlank() }.take(8)
                if(scenes.isEmpty()) {
                    Toast.makeText(this,"أضف مشهدًا واحدًا على الأقل",Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val tags=tagsField.text.toString()
                    .split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }.take(12)
                val updated=current.copy(
                    title=titleField.text.toString().trim(),
                    hook=hookField.text.toString().trim(),
                    description=descField.text.toString().trim(),
                    hashtags=tags,
                    scenes=scenes,
                    visualPrompts=emptyList()
                )
                lastContent=updated
                result.text=displayContent(updated)
                saveProject()
                status.text="تم حفظ تعديلات المحتوى ✓"
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun visualPrompts(content:BackendApiV16.GeneratedContent):List<String> {
        return content.visualPrompts.ifEmpty {
            (listOf(content.hook)+content.scenes)
                .filter { it.isNotBlank() }
                .map { "Cinematic vertical 9:16 scene, no text, no watermark: "+it }
        }
    }

    private fun generateLocalScenes() {
        val content=lastContent
        if(content==null) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        val prompts=visualPrompts(content)
        if(prompts.isEmpty()) {
            status.text="لا توجد مشاهد لتجهيزها"
            return
        }
        status.text="جاري إنشاء مشاهد محلية احترافية…"
        thread {
            try {
                val uris=LocalVisualGenerator.generate(this,prompts)
                runOnUiThread {
                    selectedImages.clear()
                    selectedImages.addAll(uris)
                    imagesStatus.text="تم إنشاء "+uris.size+" مشاهد محلية احترافية ✓"
                    status.text="المشاهد جاهزة ✓ — يمكنك إنشاء الفيديو"
                    updateScenePreview()
                    saveProject()
                }
            } catch(ex:Exception) {
                runOnUiThread {
                    status.text="تعذر إنشاء المشاهد المحلية: "+(ex.message ?: "خطأ")
                }
            }
        }
    }

    private fun generateAutomaticImages() {
        val content=lastContent
        if(content==null) {
            status.text="أنشئ المحتوى أولاً"
            return
        }
        val base=backendUrl()
        if(base.isBlank()) {
            status.text="صور AI الحقيقية تحتاج Backend متصل. استخدم «مشاهد محلية احترافية» بدون خادم."
            return
        }
        val prompts=visualPrompts(content)
        if(prompts.isEmpty()) {
            status.text="لا توجد أوصاف بصرية لتوليد الصور"
            return
        }

        autoImagesButton.isEnabled=false
        status.text="جاري توليد صور AI للمشاهد…"
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
                    imagesStatus.text="تم توليد "+newUris.size+" صور AI ✓"
                    status.text="صور AI جاهزة ✓ — يمكنك إنشاء الفيديو"
                    autoImagesButton.isEnabled=true
                    updateScenePreview()
                    saveProject()
                }
            } catch(ex:Exception) {
                runOnUiThread {
                    autoImagesButton.isEnabled=true
                    status.text="تعذر توليد صور AI: "+(ex.message ?: "تحقق من مزود الصور في Backend")
                }
            }
        }
    }

    private fun updateScenePreview() {
        val uri=selectedImages.firstOrNull()
        if(uri==null) {
            scenePreview.setImageDrawable(null)
            scenePreview.visibility=android.view.View.GONE
        } else {
            scenePreview.setImageURI(null)
            scenePreview.setImageURI(uri)
            scenePreview.visibility=android.view.View.VISIBLE
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
        if(requestCode==projectsRequestCode) {
            if(resultCode==RESULT_OK) {
                restoreProject()
                status.text="تم فتح المشروع ✓"
            }
            return
        }
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
        updateScenePreview()
        saveProject()
    }



    private fun saveProjectCopy() {
        val state=ProjectStore.State(
            topic.text.toString(),
            lastContent,
            selectedImages.toList(),
            lastVideo?.absolutePath
        )
        if(state.topic.isBlank() && state.content==null) {
            status.text="لا يوجد مشروع لحفظه"
            return
        }
        saveProject()
        val saved=ProjectStore.archiveCurrent(this,state)
        status.text="تم حفظ نسخة: "+saved.title+" ✓"
    }

    private fun openProjects() {
        startActivityForResult(Intent(this,ProjectsActivity::class.java),projectsRequestCode)
    }

    private fun saveProject() {
        ProjectStore.save(
            this,
            topic.text.toString(),
            lastContent,
            selectedImages,
            lastVideo?.absolutePath
        )
    }

    private fun restoreProject() {
        val state=ProjectStore.load(this) ?: return
        topic.setText(state.topic)
        lastContent=state.content
        selectedImages.clear()
        selectedImages.addAll(state.images)
        if(state.content!=null) result.text=displayContent(state.content)
        imagesStatus.text=if(selectedImages.isEmpty()) "لم يتم تجهيز مشاهد بعد" else "تم استرجاع "+selectedImages.size+" مشهد ✓"
        updateScenePreview()
        status.text="تم استرجاع آخر مشروع ✓"
        lastVideo=state.videoPath?.let { File(it) }?.takeIf { it.exists() }
    }

    private fun newProject() {
        lastContent=null
        lastVideo=null
        selectedImages.clear()
        topic.setText("")
        result.text=""
        imagesStatus.text="لم يتم تجهيز مشاهد بعد"
        updateScenePreview()
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
        val scenes=(listOf(content.hook)+content.scenes).filter { it.isNotBlank() }
        val narration=scenes.joinToString(". ")
        if(narration.isBlank()) {
            status.text="المحتوى لا يحتوي نصًا صالحًا للصوت"
            return
        }

        renderButton.isEnabled=false
        thread {
            try {
                runOnUiThread { status.text="1/5 تجهيز المشاهد البصرية…" }
                val renderImages=if(selectedImages.isEmpty()) {
                    LocalVisualGenerator.generate(this,visualPrompts(content)).also { generated ->
                        runOnUiThread {
                            selectedImages.clear()
                            selectedImages.addAll(generated)
                            imagesStatus.text="تم إنشاء "+generated.size+" مشاهد محلية تلقائيًا ✓"
                        }
                    }
                } else selectedImages.toList()

                runOnUiThread { status.text="2/5 جاري إنشاء الصوت العربي…" }
                val voice=ArabicTtsEngine.synthesize(this,narration)

                runOnUiThread { status.text="3/5 تم الصوت ✓ — جاري ضبط توقيت المشاهد…" }
                val mmr=MediaMetadataRetriever()
                mmr.setDataSource(voice.absolutePath)
                val duration=mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 8000L
                mmr.release()

                runOnUiThread { status.text="4/5 جاري إنشاء الفيديو العمودي…" }
                val silent=try {
                    SimpleVideoRenderer.render(this,scenes,duration+500L,renderImages)
                } catch(ex:Exception) {
                    throw IllegalStateException("مرحلة الفيديو: "+(ex.message ?: "فشل ترميز الفيديو"),ex)
                }

                runOnUiThread { status.text="5/5 جاري دمج الصوت والفيديو…" }
                val finalFile=File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
                val file=try {
                    AudioVideoMuxer.mux(silent,voice,finalFile)
                } catch(ex:Exception) {
                    throw IllegalStateException("مرحلة الدمج: "+(ex.message ?: "فشل دمج الصوت والفيديو"),ex)
                }

                lastVideo=file
                runOnUiThread {
                    status.text="تم إنشاء الفيديو الاحترافي ✓ — افتح المعاينة"
                    renderButton.isEnabled=true
                    updateScenePreview()
                    saveProject()
                }
            } catch(ex:Exception) {
                runOnUiThread {
                    status.text="فشل إنشاء الفيديو: "+(ex.message ?: "خطأ غير معروف")
                    renderButton.isEnabled=true
                }
            }
        }
    }

    private fun previewVideo() {
        val file=lastVideo ?: File(getExternalFilesDir(null),"tiktok_ai_final.mp4")
        if(!file.exists()) {
            status.text="لا يوجد فيديو بعد — أنشئ الفيديو أولاً"
            return
        }
        val intent=Intent(this,PreviewActivity::class.java).apply {
            putExtra("video_path",file.absolutePath)
        }
        try { startActivity(intent) } catch(ex:Exception) {
            status.text="تعذر فتح معاينة الفيديو"
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

    private fun offlineContent(idea:String)=LocalContentGenerator.generate(idea)

    private fun generate() {
        val idea=topic.text.toString().trim()
        if(idea.isEmpty()) {
            status.text="اكتب فكرة الفيديو أولاً"
            return
        }
        val backend=backendUrl()
        status.text=if(backend.isBlank()) "جاري إنشاء محتوى محلي…" else "جاري إنشاء المحتوى بالذكاء الاصطناعي…"
        result.text=""
        if(backend.isBlank()) {
            val c=offlineContent(idea)
            lastContent=c
            result.text=displayContent(c)
            status.text="تم إنشاء محتوى محلي ذكي ✓"
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
                    updateScenePreview()
                    saveProject()
                }
            } catch(ex:Exception) {
                val c=offlineContent(idea)
                lastContent=c
                val text=displayContent(c)
                runOnUiThread {
                    result.text=text
                    status.text="الخادم غير متصل — تم استخدام المولد المحلي ✓"
                    updateScenePreview()
                    saveProject()
                }
            }
        }
    }
}
