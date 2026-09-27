package com.example.tiktokai

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Date

class ProjectsActivity : AppCompatActivity() {
    private lateinit var listRoot:LinearLayout
    private lateinit var empty:TextView

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val outer=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(18),dp(18),dp(18))
            setBackgroundColor(Color.rgb(11,13,18))
        }
        val title=TextView(this).apply {
            text="مشاريعي"
            textSize=28f
            setTextColor(Color.WHITE)
        }
        val subtitle=TextView(this).apply {
            text="النسخ المحفوظة محليًا على هذا الجهاز"
            setTextColor(Color.LTGRAY)
            setPadding(0,0,0,dp(12))
        }
        empty=TextView(this).apply {
            text="لا توجد مشاريع محفوظة بعد"
            setTextColor(Color.LTGRAY)
            setPadding(0,dp(24),0,dp(24))
        }
        val scroll=ScrollView(this)
        listRoot=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(listRoot)
        val close=Button(this).apply {
            text="إغلاق"
            setOnClickListener { finish() }
        }
        outer.addView(title)
        outer.addView(subtitle)
        outer.addView(empty)
        outer.addView(scroll,LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,0,1f))
        outer.addView(close)
        setContentView(outer)
        refresh()
    }

    private fun refresh() {
        val projects=ProjectStore.listSaved(this)
        listRoot.removeAllViews()
        empty.visibility=if(projects.isEmpty()) View.VISIBLE else View.GONE
        projects.forEach { project ->
            val btn=Button(this).apply {
                isAllCaps=false
                text=project.title+"\n"+DateFormat.getMediumDateFormat(this@ProjectsActivity)
                    .format(Date(project.updatedAt))
                setOnClickListener { openOptions(project) }
            }
            listRoot.addView(btn)
        }
    }

    private fun openOptions(project:ProjectStore.SavedProject) {
        AlertDialog.Builder(this)
            .setTitle(project.title)
            .setItems(arrayOf("فتح المشروع","حذف المشروع")) { _,which ->
                when(which) {
                    0 -> {
                        if(ProjectStore.openSaved(this,project.id)) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                    1 -> AlertDialog.Builder(this)
                        .setTitle("حذف المشروع؟")
                        .setMessage("سيتم حذف النسخة المحفوظة من المكتبة.")
                        .setNegativeButton("إلغاء",null)
                        .setPositiveButton("حذف") { _,_ ->
                            ProjectStore.deleteSaved(this,project.id)
                            refresh()
                        }.show()
                }
            }
            .setNegativeButton("إلغاء",null)
            .show()
    }

    private fun dp(value:Int):Int=(value*resources.displayMetrics.density).toInt()
}
