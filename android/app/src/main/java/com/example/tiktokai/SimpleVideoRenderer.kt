package com.example.tiktokai

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import kotlin.math.max
import kotlin.math.min

object SimpleVideoRenderer {
    private const val W=720
    private const val H=1280
    private const val FPS=15

    fun render(
        context:Context,
        scenes:List<String>,
        durationMs:Long=8000L,
        imageUris:List<Uri> = emptyList()
    ):File {
        val out=File(context.getExternalFilesDir(null),"tiktok_ai_latest.mp4")
        if(out.exists()) out.delete()
        val safe=if(scenes.isEmpty()) listOf(" ") else scenes
        val images=imageUris.mapNotNull { decodeImage(context,it) }
        val frames=((durationMs.coerceIn(3000L,180000L)*FPS+999)/1000).toInt()
        val weights=safe.map { it.trim().length.coerceIn(20,120) }
        val totalWeight=weights.sum().coerceAtLeast(1)
        val starts=IntArray(safe.size)
        val ends=IntArray(safe.size)
        var accWeight=0
        for(i in safe.indices) {
            starts[i]=(accWeight.toLong()*frames/totalWeight).toInt()
            accWeight+=weights[i]
            ends[i]=(accWeight.toLong()*frames/totalWeight).toInt().coerceAtLeast(starts[i]+1)
        }

        val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,W,H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE,3_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE,FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
        }
        val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val mux=MediaMuxer(out.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info=MediaCodec.BufferInfo()
        var track=-1
        var started=false

        try {
            for(frame in 0 until frames) {
                var idx=ends.indexOfFirst { frame < it }
                if(idx<0) idx=safe.lastIndex
                val sceneStart=starts[idx]
                val sceneEnd=ends[idx]
                val sceneProgress=((frame-sceneStart).toFloat()/(sceneEnd-sceneStart).coerceAtLeast(1)).coerceIn(0f,1f)
                val image=if(images.isEmpty()) null else images[idx % images.size]
                val nextImage=if(images.isEmpty() || idx>=safe.lastIndex) null else images[(idx+1) % images.size]
                val bitmap=drawFrame(
                    safe[idx],idx,safe.size,frame,frames,image,nextImage,sceneProgress
                )
                val yuv=argbToI420(bitmap)
                bitmap.recycle()

                var queued=false
                while(!queued) {
                    val input=codec.dequeueInputBuffer(10_000)
                    if(input>=0) {
                        val b=codec.getInputBuffer(input)!!
                        b.clear()
                        b.put(yuv)
                        codec.queueInputBuffer(input,0,yuv.size,frame*1_000_000L/FPS,0)
                        queued=true
                    }
                    val state=drain(codec,mux,info,track,started)
                    track=state.first
                    started=state.second
                }
            }

            var eos=false
            while(!eos) {
                val input=codec.dequeueInputBuffer(10_000)
                if(input>=0) {
                    codec.queueInputBuffer(input,0,0,frames*1_000_000L/FPS,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eos=true
                } else {
                    val state=drain(codec,mux,info,track,started)
                    track=state.first
                    started=state.second
                }
            }

            var done=false
            while(!done) {
                val i=codec.dequeueOutputBuffer(info,10_000)
                if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                    track=mux.addTrack(codec.outputFormat)
                    mux.start()
                    started=true
                } else if(i>=0) {
                    val b=codec.getOutputBuffer(i)
                    if(info.size>0 && started && b!=null) {
                        b.position(info.offset)
                        b.limit(info.offset+info.size)
                        mux.writeSampleData(track,b,info)
                    }
                    done=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    codec.releaseOutputBuffer(i,false)
                }
            }
        } finally {
            images.forEach { if(!it.isRecycled) it.recycle() }
            codec.stop()
            codec.release()
            if(started) mux.stop()
            mux.release()
        }
        return out
    }

    private fun decodeImage(context:Context,uri:Uri):Bitmap? {
        return try {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            if(uri.scheme=="file") {
                uri.path?.let { BitmapFactory.decodeFile(it,bounds) }
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
            }
            if(bounds.outWidth<=0 || bounds.outHeight<=0) return null
            var sample=1
            while(bounds.outWidth/sample>1600 || bounds.outHeight/sample>1600) sample*=2
            val opts=BitmapFactory.Options().apply {
                inSampleSize=sample
                inPreferredConfig=Bitmap.Config.ARGB_8888
            }
            if(uri.scheme=="file") {
                uri.path?.let { BitmapFactory.decodeFile(it,opts) }
            } else {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,opts) }
            }
        } catch(_:Exception) {
            null
        }
    }

    private fun drain(
        codec:MediaCodec,
        mux:MediaMuxer,
        info:MediaCodec.BufferInfo,
        initialTrack:Int,
        initialStarted:Boolean
    ):Pair<Int,Boolean> {
        var track=initialTrack
        var started=initialStarted
        while(true) {
            val i=codec.dequeueOutputBuffer(info,0)
            if(i==MediaCodec.INFO_TRY_AGAIN_LATER) break
            if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                track=mux.addTrack(codec.outputFormat)
                mux.start()
                started=true
            } else if(i>=0) {
                val b=codec.getOutputBuffer(i)
                if(info.size>0 && started && b!=null) {
                    b.position(info.offset)
                    b.limit(info.offset+info.size)
                    mux.writeSampleData(track,b,info)
                }
                codec.releaseOutputBuffer(i,false)
            }
        }
        return Pair(track,started)
    }

    private fun drawFrame(
        text:String,
        scene:Int,
        total:Int,
        frame:Int,
        frames:Int,
        image:Bitmap?,
        nextImage:Bitmap?,
        sceneProgress:Float
    ):Bitmap {
        val bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bm)
        canvas.drawColor(Color.rgb(8,10,18))

        if(image!=null) {
            drawCover(canvas,image,sceneProgress,255,if(scene%2==0) 1f else -1f)
            if(nextImage!=null && sceneProgress>0.88f) {
                val alpha=(((sceneProgress-0.88f)/0.12f)*255f).toInt().coerceIn(0,255)
                drawCover(canvas,nextImage,0f,alpha,if(scene%2==0) -1f else 1f)
            }
        } else {
            val fallback=Paint().apply {
                shader=LinearGradient(
                    0f,0f,W.toFloat(),H.toFloat(),
                    intArrayOf(Color.rgb(16,24,42),Color.rgb(31,22,63),Color.rgb(7,10,19)),
                    null,Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f,0f,W.toFloat(),H.toFloat(),fallback)
        }

        val shade=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(
                0f,260f,0f,H.toFloat(),
                intArrayOf(
                    Color.argb(18,0,0,0),
                    Color.argb(45,0,0,0),
                    Color.argb(185,0,0,0),
                    Color.argb(235,0,0,0)
                ),
                floatArrayOf(0f,0.32f,0.70f,1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f,0f,W.toFloat(),H.toFloat(),shade)

        val enter=(sceneProgress/0.12f).coerceIn(0f,1f)
        val leave=((1f-sceneProgress)/0.09f).coerceIn(0f,1f)
        val contentAlpha=(255f*min(enter,leave)).toInt().coerceIn(0,255)
        val lift=(1f-enter)*34f

        drawSceneChip(canvas,scene,total,contentAlpha,90f+lift)

        val overlay=compactText(text)
        val textPaint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE
            textSize=46f
            typeface=Typeface.create("sans-serif",Typeface.BOLD)
            alpha=contentAlpha
        }
        val textWidth=560
        val layout=StaticLayout.Builder
            .obtain(overlay,0,overlay.length,textPaint,textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(false)
            .setLineSpacing(5f,1.04f)
            .setMaxLines(4)
            .build()

        val cardTop=(H*0.63f-layout.height*0.5f+lift).coerceIn(650f,820f)
        val cardLeft=54f
        val cardRight=W-54f
        val cardBottom=cardTop+layout.height+92f

        val card=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb((172f*(contentAlpha/255f)).toInt(),8,12,22)
        }
        val border=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb((52f*(contentAlpha/255f)).toInt(),255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=2f
        }
        canvas.drawRoundRect(RectF(cardLeft,cardTop,cardRight,cardBottom),32f,32f,card)
        canvas.drawRoundRect(RectF(cardLeft,cardTop,cardRight,cardBottom),32f,32f,border)

        val accent=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(contentAlpha,94,234,212)
        }
        canvas.drawRoundRect(RectF(cardLeft+26f,cardTop+24f,cardLeft+100f,cardTop+30f),3f,3f,accent)

        canvas.save()
        canvas.translate((W-textWidth)/2f,cardTop+52f)
        layout.draw(canvas)
        canvas.restore()

        drawProgress(canvas,scene,total,sceneProgress)
        return bm
    }

    private fun drawCover(canvas:Canvas,image:Bitmap,progress:Float,alpha:Int,direction:Float) {
        val base=max(W.toFloat()/image.width,H.toFloat()/image.height)
        val scale=base*(1.025f+0.055f*progress)
        val dw=image.width*scale
        val dh=image.height*scale
        val overflowX=(dw-W).coerceAtLeast(0f)
        val overflowY=(dh-H).coerceAtLeast(0f)
        val panX=(progress-0.5f)*overflowX*0.42f*direction
        val panY=(progress-0.5f)*overflowY*0.12f
        val left=(W-dw)/2f-panX
        val top=(H-dh)/2f-panY
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha=alpha }
        canvas.drawBitmap(image,null,RectF(left,top,left+dw,top+dh),paint)
    }

    private fun drawSceneChip(canvas:Canvas,scene:Int,total:Int,alpha:Int,y:Float) {
        val rect=RectF(54f,y,194f,y+58f)
        val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb((105f*(alpha/255f)).toInt(),8,12,22)
        }
        val border=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb((55f*(alpha/255f)).toInt(),255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=2f
        }
        canvas.drawRoundRect(rect,29f,29f,fill)
        canvas.drawRoundRect(rect,29f,29f,border)

        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(alpha,255,255,255)
            textSize=24f
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
            textAlign=Paint.Align.CENTER
        }
        val label=(scene+1).toString().padStart(2,'0')+" / "+total.toString().padStart(2,'0')
        canvas.drawText(label,rect.centerX(),rect.centerY()+8f,p)
    }

    private fun drawProgress(canvas:Canvas,scene:Int,total:Int,sceneProgress:Float) {
        val y=H-70f
        val gap=12f
        val available=W-108f
        val segment=(available-gap*(total-1))/total.coerceAtLeast(1)
        for(i in 0 until total) {
            val left=54f+i*(segment+gap)
            val bg=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(70,255,255,255) }
            canvas.drawRoundRect(RectF(left,y,left+segment,y+7f),4f,4f,bg)
            if(i<scene) {
                val done=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(225,255,255,255) }
                canvas.drawRoundRect(RectF(left,y,left+segment,y+7f),4f,4f,done)
            } else if(i==scene) {
                val active=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(94,234,212) }
                canvas.drawRoundRect(
                    RectF(left,y,left+segment*sceneProgress.coerceIn(0f,1f),y+7f),
                    4f,4f,active
                )
            }
        }
    }

    private fun compactText(text:String):String {
        val clean=text.replace(Regex("\\s+")," ").trim()
        if(clean.length<=112) return clean
        val cut=clean.take(112)
        val boundary=maxOf(cut.lastIndexOf(' '),cut.lastIndexOf('،'),cut.lastIndexOf('؛'))
        val trimmed=if(boundary>72) cut.substring(0,boundary) else cut
        return trimmed.trimEnd(' ',',','،','؛','.')+"…"
    }

    private fun argbToI420(bm:Bitmap):ByteArray {
        val pixels=IntArray(W*H)
        bm.getPixels(pixels,0,W,0,0,W,H)
        val out=ByteArray(W*H*3/2)
        var yi=0
        var ui=W*H
        var vi=ui+W*H/4
        for(y in 0 until H) for(x in 0 until W) {
            val p=pixels[y*W+x]
            val r=(p shr 16) and 255
            val g=(p shr 8) and 255
            val b=p and 255
            out[yi++]=(((66*r+129*g+25*b+128 shr 8)+16).coerceIn(0,255)).toByte()
            if(y%2==0 && x%2==0) {
                out[ui++]=(((-38*r-74*g+112*b+128 shr 8)+128).coerceIn(0,255)).toByte()
                out[vi++]=(((112*r-94*g-18*b+128 shr 8)+128).coerceIn(0,255)).toByte()
            }
        }
        return out
    }
}
