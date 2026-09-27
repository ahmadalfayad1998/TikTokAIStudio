package com.example.tiktokai

import android.content.Context
import android.graphics.*
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

object LocalVisualGenerator {
    private const val W=720
    private const val H=1280

    fun generate(context:Context,prompts:List<String>):List<Uri> {
        val dir=File(context.getExternalFilesDir("visuals"),"local").apply { mkdirs() }
        dir.listFiles()?.forEach { if(it.isFile) it.delete() }
        return prompts.take(9).mapIndexed { index,prompt ->
            val file=File(dir,"scene_"+(index+1)+".png")
            val bitmap=createVisual(prompt,index)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG,92,it) }
            bitmap.recycle()
            Uri.fromFile(file)
        }
    }

    private fun createVisual(prompt:String,index:Int):Bitmap {
        val bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        val c=Canvas(bm)
        val seed=(prompt.hashCode().toLong() shl 16) xor index.toLong()
        val rnd=Random(seed)
        val palettes=arrayOf(
            intArrayOf(Color.rgb(13,20,45),Color.rgb(17,94,89),Color.rgb(9,14,30)),
            intArrayOf(Color.rgb(42,16,58),Color.rgb(88,28,135),Color.rgb(15,12,30)),
            intArrayOf(Color.rgb(15,23,42),Color.rgb(30,64,175),Color.rgb(8,15,33)),
            intArrayOf(Color.rgb(35,18,18),Color.rgb(153,27,27),Color.rgb(20,12,16)),
            intArrayOf(Color.rgb(20,30,22),Color.rgb(22,101,52),Color.rgb(7,18,13))
        )
        val pal=palettes[Math.floorMod(prompt.hashCode()+index,palettes.size)]
        val bg=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(0f,0f,W.toFloat(),H.toFloat(),pal,null,Shader.TileMode.CLAMP)
        }
        c.drawRect(0f,0f,W.toFloat(),H.toFloat(),bg)

        val haze=Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(12) {
            haze.color=Color.argb(18+rnd.nextInt(26),180+rnd.nextInt(76),180+rnd.nextInt(76),180+rnd.nextInt(76))
            val r=80f+rnd.nextFloat()*230f
            c.drawCircle(rnd.nextFloat()*W,rnd.nextFloat()*H,r,haze)
        }

        val lower=prompt.lowercase()
        when {
            lower.contains("internet") || lower.contains("network") || lower.contains("إنترنت") || lower.contains("الإنترنت") || lower.contains("world") || lower.contains("العالم") ->
                drawNetworkGlobe(c,rnd)
            lower.contains("phone") || lower.contains("هاتف") || lower.contains("mobile") ->
                drawPhone(c,rnd)
            lower.contains("money") || lower.contains("bank") || lower.contains("مال") || lower.contains("اقتصاد") ->
                drawFinance(c,rnd)
            lower.contains("space") || lower.contains("planet") || lower.contains("فضاء") || lower.contains("كوكب") ->
                drawSpace(c,rnd)
            else -> drawAbstractScene(c,rnd)
        }

        val vignette=Paint().apply {
            shader=RadialGradient(W/2f,H/2f,760f,
                intArrayOf(Color.TRANSPARENT,Color.argb(190,0,0,0)),
                floatArrayOf(0.45f,1f),Shader.TileMode.CLAMP)
        }
        c.drawRect(0f,0f,W.toFloat(),H.toFloat(),vignette)
        return bm
    }

    private fun drawNetworkGlobe(c:Canvas,rnd:Random) {
        val cx=W/2f; val cy=H*0.46f; val radius=235f
        val glow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(cx,cy,radius*1.3f,
                intArrayOf(Color.argb(180,94,234,212),Color.argb(20,94,234,212),Color.TRANSPARENT),
                null,Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx,cy,radius*1.3f,glow)
        val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(190,210,255,248);style=Paint.Style.STROKE;strokeWidth=3f
        }
        c.drawCircle(cx,cy,radius,line)
        for(i in -2..2) {
            val rr=radius*(1f-kotlin.math.abs(i)*0.12f)
            c.drawOval(RectF(cx-rr,cy-radius*0.34f+i*70f,cx+rr,cy+radius*0.34f+i*70f),line)
        }
        for(a in intArrayOf(-55,-28,0,28,55)) {
            val rect=RectF(cx-radius*0.42f,cy-radius,cx+radius*0.42f,cy+radius)
            c.save();c.rotate(a.toFloat(),cx,cy);c.drawOval(rect,line);c.restore()
        }
        val node=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
        val pts=mutableListOf<PointF>()
        repeat(18) {
            val ang=rnd.nextFloat()*Math.PI.toFloat()*2f
            val rr=radius*(0.2f+rnd.nextFloat()*0.72f)
            pts.add(PointF(cx+cos(ang)*rr,cy+sin(ang)*rr*0.72f))
        }
        pts.forEachIndexed { i,p ->
            c.drawCircle(p.x,p.y,6f,node)
            if(i>0 && i%2==0) c.drawLine(p.x,p.y,pts[i-1].x,pts[i-1].y,line)
        }
    }

    private fun drawPhone(c:Canvas,rnd:Random) {
        val body=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(230,12,18,30) }
        val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=7f }
        val rect=RectF(205f,270f,515f,900f)
        c.drawRoundRect(rect,42f,42f,body);c.drawRoundRect(rect,42f,42f,stroke)
        val screen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(230f,320f,490f,850f,
                intArrayOf(Color.rgb(94,234,212),Color.rgb(59,130,246)),null,Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(232f,330f,488f,830f),24f,24f,screen)
        repeat(8) {
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(150+rnd.nextInt(80),255,255,255) }
            c.drawCircle(280f+rnd.nextFloat()*160f,420f+rnd.nextFloat()*300f,12f+rnd.nextFloat()*28f,p)
        }
    }

    private fun drawFinance(c:Canvas,rnd:Random) {
        val coin=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(W/2f,H*0.42f,210f,
                intArrayOf(Color.rgb(254,240,138),Color.rgb(245,158,11),Color.rgb(120,53,15)),
                null,Shader.TileMode.CLAMP)
        }
        c.drawCircle(W/2f,H*0.42f,210f,coin)
        val bars=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(210,255,255,255) }
        for(i in 0..5) {
            val bh=100f+i*75f+rnd.nextFloat()*70f
            c.drawRoundRect(120f+i*85f,H-260f-bh,175f+i*85f,H-260f,18f,18f,bars)
        }
    }

    private fun drawSpace(c:Canvas,rnd:Random) {
        val star=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
        repeat(120) { c.drawCircle(rnd.nextFloat()*W,rnd.nextFloat()*H,1f+rnd.nextFloat()*3f,star) }
        val planet=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(200f,350f,530f,720f,
                intArrayOf(Color.rgb(96,165,250),Color.rgb(124,58,237),Color.rgb(30,27,75)),
                null,Shader.TileMode.CLAMP)
        }
        c.drawCircle(W/2f,H*0.48f,220f,planet)
        val ring=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(180,255,255,255);style=Paint.Style.STROKE;strokeWidth=13f }
        c.save();c.rotate(-18f,W/2f,H*0.48f)
        c.drawOval(RectF(90f,H*0.40f,630f,H*0.56f),ring);c.restore()
    }

    private fun drawAbstractScene(c:Canvas,rnd:Random) {
        val line=Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth=9f;style=Paint.Style.STROKE }
        repeat(6) { i ->
            line.color=Color.argb(90+rnd.nextInt(100),140+rnd.nextInt(116),140+rnd.nextInt(116),180+rnd.nextInt(76))
            val path=Path()
            path.moveTo(40f,H*(0.22f+i*0.09f))
            path.cubicTo(180f+rnd.nextFloat()*100f,250f+rnd.nextFloat()*700f,
                430f+rnd.nextFloat()*100f,250f+rnd.nextFloat()*700f,
                W-40f,H*(0.25f+i*0.10f))
            c.drawPath(path,line)
        }
        val block=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(100,255,255,255) }
        repeat(9) {
            val x=80f+rnd.nextFloat()*(W-160f)
            val y=280f+rnd.nextFloat()*650f
            val r=28f+rnd.nextFloat()*70f
            c.drawCircle(x,y,r,block)
        }
    }
}
