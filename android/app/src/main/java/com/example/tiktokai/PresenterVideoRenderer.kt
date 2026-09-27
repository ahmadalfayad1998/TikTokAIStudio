package com.example.tiktokai

import android.content.Context
import android.graphics.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import kotlin.math.abs
import kotlin.math.sin

object PresenterVideoRenderer {
    private const val W=720
    private const val H=1280
    private const val FPS=15

    fun render(
        context:Context,
        captions:List<String>,
        audioFile:File,
        durationMs:Long,
        narrationWeights:List<Int>
    ):File {
        val safe=if(captions.isEmpty()) listOf(" ") else captions
        val frames=((durationMs.coerceIn(3000L,180000L)*FPS+999)/1000).toInt()
        val weights=safe.indices.map { i ->
            (narrationWeights.getOrNull(i) ?: safe[i].length).coerceIn(20,220)
        }
        val totalWeight=weights.sum().coerceAtLeast(1)
        val starts=IntArray(safe.size)
        val ends=IntArray(safe.size)
        var acc=0
        for(i in safe.indices) {
            starts[i]=(acc.toLong()*frames/totalWeight).toInt()
            acc+=weights[i]
            ends[i]=(acc.toLong()*frames/totalWeight).toInt().coerceAtLeast(starts[i]+1)
        }

        val envelope=runCatching { AudioEnvelope.fromAudioFile(audioFile) }.getOrNull()
        val out=File(context.getExternalFilesDir(null),"tiktok_ai_presenter_silent.mp4")
        if(out.exists()) out.delete()

        val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,W,H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE,3_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE,FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
        }
        val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val mux=MediaMuxer(out.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info=MediaCodec.BufferInfo()
        var track=-1
        var muxStarted=false
        val frameBitmap=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        val pixels=IntArray(W*H)
        val yuv=ByteArray(W*H*3/2)

        try {
            for(frame in 0 until frames) {
                var scene=ends.indexOfFirst { frame<it }
                if(scene<0) scene=safe.lastIndex
                val start=starts[scene]
                val end=ends[scene]
                val sceneProgress=((frame-start).toFloat()/(end-start).coerceAtLeast(1)).coerceIn(0f,1f)
                val timeUs=frame*1_000_000L/FPS
                val amp=envelope?.amplitudeAt(timeUs) ?: syntheticAmplitude(frame)
                drawFrame(frameBitmap,safe[scene],scene,safe.size,sceneProgress,amp,frame)
                argbToI420(frameBitmap,pixels,yuv)

                var queued=false
                while(!queued) {
                    val input=codec.dequeueInputBuffer(10_000)
                    if(input>=0) {
                        val b=codec.getInputBuffer(input)!!
                        b.clear()
                        b.put(yuv)
                        codec.queueInputBuffer(input,0,yuv.size,timeUs,0)
                        queued=true
                    }
                    val state=drain(codec,mux,info,track,muxStarted)
                    track=state.first
                    muxStarted=state.second
                }
            }

            var eos=false
            while(!eos) {
                val input=codec.dequeueInputBuffer(10_000)
                if(input>=0) {
                    codec.queueInputBuffer(
                        input,0,0,frames*1_000_000L/FPS,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    eos=true
                } else {
                    val state=drain(codec,mux,info,track,muxStarted)
                    track=state.first
                    muxStarted=state.second
                }
            }

            var done=false
            while(!done) {
                val index=codec.dequeueOutputBuffer(info,10_000)
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxStarted) {
                    track=mux.addTrack(codec.outputFormat)
                    mux.start()
                    muxStarted=true
                } else if(index>=0) {
                    val buffer=codec.getOutputBuffer(index)
                    if(info.size>0 && muxStarted && buffer!=null) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset+info.size)
                        mux.writeSampleData(track,buffer,info)
                    }
                    done=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    codec.releaseOutputBuffer(index,false)
                }
            }
        } finally {
            if(!frameBitmap.isRecycled) frameBitmap.recycle()
            codec.stop()
            codec.release()
            if(muxStarted) mux.stop()
            mux.release()
        }

        if(!out.exists() || out.length()==0L) {
            throw IllegalStateException("فشل إنشاء فيديو المقدم المحلي")
        }
        return out
    }

    private fun drawFrame(
        bitmap:Bitmap,
        caption:String,
        scene:Int,
        total:Int,
        progress:Float,
        amplitude:Float,
        frame:Int
    ) {
        val c=Canvas(bitmap)
        c.drawColor(Color.rgb(7,10,18),PorterDuff.Mode.SRC)

        val bg=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(
                0f,0f,W.toFloat(),H.toFloat(),
                intArrayOf(
                    Color.rgb(8,15,31),
                    Color.rgb(19,35,57),
                    Color.rgb(7,10,18)
                ),
                floatArrayOf(0f,0.55f,1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f,0f,W.toFloat(),H.toFloat(),bg)

        drawStudio(c,frame)
        drawPresenter(c,amplitude,frame)
        drawCaption(c,caption,scene,total,progress)
    }

    private fun drawStudio(c:Canvas,frame:Int) {
        val glow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                360f,330f,430f,
                intArrayOf(
                    Color.argb(80,45,212,191),
                    Color.argb(18,59,130,246),
                    Color.TRANSPARENT
                ),
                null,Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(360f,330f,430f,glow)

        val panel=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(22,255,255,255) }
        c.drawRoundRect(RectF(42f,70f,210f,560f),38f,38f,panel)
        c.drawRoundRect(RectF(510f,100f,678f,540f),38f,38f,panel)

        val wave=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(78,94,234,212)
            strokeWidth=5f
            style=Paint.Style.STROKE
            strokeCap=Paint.Cap.ROUND
        }
        val phase=frame*0.16f
        val path=Path()
        path.moveTo(65f,425f)
        for(x in 65..190 step 8) {
            val y=425f+sin((x-65)*0.08f+phase)*22f
            path.lineTo(x.toFloat(),y)
        }
        c.drawPath(path,wave)

        val screen=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(
                530f,150f,660f,480f,
                intArrayOf(Color.argb(60,96,165,250),Color.argb(16,168,85,247)),
                null,Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(RectF(535f,155f,653f,472f),24f,24f,screen)
        val border=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(55,255,255,255)
            style=Paint.Style.STROKE
            strokeWidth=2f
        }
        c.drawRoundRect(RectF(535f,155f,653f,472f),24f,24f,border)
    }

    private fun drawPresenter(c:Canvas,amp:Float,frame:Int) {
        val bob=sin(frame*0.055f)*3.5f
        val centerX=360f
        val headY=350f+bob

        // Torso / suit.
        val suit=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=LinearGradient(
                175f,620f,545f,1040f,
                intArrayOf(Color.rgb(22,31,49),Color.rgb(8,13,24)),
                null,Shader.TileMode.CLAMP
            )
        }
        val torso=Path()
        torso.moveTo(210f,650f)
        torso.cubicTo(250f,600f,300f,585f,360f,585f)
        torso.cubicTo(420f,585f,485f,605f,515f,660f)
        torso.lineTo(585f,1035f)
        torso.lineTo(135f,1035f)
        torso.close()
        c.drawPath(torso,suit)

        // Shirt and lapels.
        val shirt=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(224,231,239) }
        val shirtPath=Path()
        shirtPath.moveTo(315f,610f)
        shirtPath.lineTo(360f,785f)
        shirtPath.lineTo(405f,610f)
        shirtPath.close()
        c.drawPath(shirtPath,shirt)

        val lapel=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(30,43,65) }
        val left=Path().apply {
            moveTo(225f,645f);lineTo(315f,605f);lineTo(360f,785f);lineTo(275f,700f);close()
        }
        val right=Path().apply {
            moveTo(495f,645f);lineTo(405f,605f);lineTo(360f,785f);lineTo(445f,700f);close()
        }
        c.drawPath(left,lapel);c.drawPath(right,lapel)

        val tie=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(13,148,136) }
        val tiePath=Path().apply {
            moveTo(344f,650f);lineTo(376f,650f);lineTo(386f,770f);lineTo(360f,815f);lineTo(334f,770f);close()
        }
        c.drawPath(tiePath,tie)

        // Neck.
        val skin=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(207,154,118) }
        c.drawRoundRect(RectF(315f,515f+bob,405f,650f+bob),30f,30f,skin)

        // Ears.
        c.drawOval(RectF(224f,335f+bob,275f,430f+bob),skin)
        c.drawOval(RectF(445f,335f+bob,496f,430f+bob),skin)

        // Face.
        val face=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(
                330f,320f+bob,225f,
                intArrayOf(Color.rgb(232,183,146),Color.rgb(195,137,105)),
                null,Shader.TileMode.CLAMP
            )
        }
        c.drawOval(RectF(250f,165f+bob,470f,575f+bob),face)

        // Hair.
        val hair=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(25,23,24) }
        val hairPath=Path().apply {
            moveTo(260f,290f+bob)
            cubicTo(245f,205f+bob,285f,145f+bob,360f,140f+bob)
            cubicTo(435f,138f+bob,478f,205f+bob,463f,287f+bob)
            cubicTo(430f,245f+bob,400f,230f+bob,360f,232f+bob)
            cubicTo(325f,230f+bob,292f,245f+bob,260f,290f+bob)
            close()
        }
        c.drawPath(hairPath,hair)

        // Beard/stubble.
        val beard=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(42,35,31,30) }
        val jaw=Path().apply {
            moveTo(274f,430f+bob)
            cubicTo(290f,535f+bob,330f,570f+bob,360f,572f+bob)
            cubicTo(405f,570f+bob,438f,525f+bob,451f,430f+bob)
            cubicTo(425f,500f+bob,395f,530f+bob,360f,532f+bob)
            cubicTo(323f,528f+bob,298f,495f+bob,274f,430f+bob)
            close()
        }
        c.drawPath(jaw,beard)

        val blink=(frame%61 in 0..2)
        val eye=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(29,24,24) }
        if(blink) {
            val eyeLine=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color=Color.rgb(50,35,30);strokeWidth=5f;strokeCap=Paint.Cap.ROUND
            }
            c.drawLine(296f,350f+bob,331f,350f+bob,eyeLine)
            c.drawLine(389f,350f+bob,424f,350f+bob,eyeLine)
        } else {
            c.drawOval(RectF(299f,340f+bob,330f,362f+bob),eye)
            c.drawOval(RectF(390f,340f+bob,421f,362f+bob),eye)
            val shine=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE }
            c.drawCircle(318f,347f+bob,3.5f,shine)
            c.drawCircle(409f,347f+bob,3.5f,shine)
        }

        // Brows.
        val brow=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.rgb(62,42,35);strokeWidth=8f;strokeCap=Paint.Cap.ROUND
        }
        c.drawLine(292f,318f+bob,334f,313f+bob,brow)
        c.drawLine(386f,313f+bob,428f,318f+bob,brow)

        // Nose.
        val nose=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(90,95,60,45);strokeWidth=4f;style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND
        }
        c.drawLine(360f,360f+bob,350f,418f+bob,nose)
        c.drawArc(RectF(342f,407f+bob,380f,435f+bob),20f,140f,false,nose)

        // Mouth synced to audio envelope.
        val open=(4f+amp.coerceIn(0f,1f)*27f)
        val mouthY=470f+bob
        val mouth=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(85,30,31) }
        c.drawOval(RectF(326f,mouthY-open/2f,394f,mouthY+open/2f),mouth)
        if(open>11f) {
            val teeth=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(237,224,214) }
            c.drawRoundRect(RectF(338f,mouthY-open/2f+3f,382f,mouthY-open/2f+8f),3f,3f,teeth)
        }
        val lip=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(150,117,55,54);style=Paint.Style.STROKE;strokeWidth=3f
        }
        c.drawOval(RectF(326f,mouthY-open/2f,394f,mouthY+open/2f),lip)
    }

    private fun drawCaption(c:Canvas,text:String,scene:Int,total:Int,progress:Float) {
        val chip=RectF(50f,58f,182f,112f)
        c.drawRoundRect(chip,27f,27f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(110,6,12,22) })
        c.drawRoundRect(chip,27f,27f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(80,255,255,255);style=Paint.Style.STROKE;strokeWidth=2f
        })
        val scenePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE;textSize=23f;textAlign=Paint.Align.CENTER
            typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        }
        c.drawText((scene+1).toString().padStart(2,'0')+" / "+total.toString().padStart(2,'0'),chip.centerX(),93f,scenePaint)

        val card=RectF(48f,850f,W-48f,1132f)
        c.drawRoundRect(card,34f,34f,Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.argb(205,5,10,18) })
        c.drawRoundRect(card,34f,34f,Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.argb(65,255,255,255);style=Paint.Style.STROKE;strokeWidth=2f
        })
        c.drawRoundRect(RectF(76f,882f,155f,889f),4f,4f,Paint().apply { color=Color.rgb(45,212,191) })

        val clean=text.replace(Regex("\\s+")," ").trim().take(95)
        val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.WHITE;textSize=42f
            typeface=Typeface.create("sans-serif",Typeface.BOLD)
        }
        val layout=StaticLayout.Builder.obtain(clean,0,clean.length,tp,540)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(false)
            .setLineSpacing(4f,1.03f)
            .setMaxLines(3)
            .build()
        c.save()
        c.translate(90f,925f)
        layout.draw(c)
        c.restore()

        val y=1200f
        val gap=10f
        val available=W-100f
        val seg=(available-gap*(total-1))/total.coerceAtLeast(1)
        for(i in 0 until total) {
            val left=50f+i*(seg+gap)
            c.drawRoundRect(RectF(left,y,left+seg,y+7f),4f,4f,Paint().apply { color=Color.argb(55,255,255,255) })
            if(i<scene) {
                c.drawRoundRect(RectF(left,y,left+seg,y+7f),4f,4f,Paint().apply { color=Color.WHITE })
            } else if(i==scene) {
                c.drawRoundRect(
                    RectF(left,y,left+seg*progress.coerceIn(0f,1f),y+7f),
                    4f,4f,Paint().apply { color=Color.rgb(45,212,191) }
                )
            }
        }
    }

    private fun syntheticAmplitude(frame:Int):Float =
        (0.18f+0.35f*abs(sin(frame*0.41f))).coerceIn(0f,1f)

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
            val index=codec.dequeueOutputBuffer(info,0)
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER) break
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                track=mux.addTrack(codec.outputFormat)
                mux.start()
                started=true
            } else if(index>=0) {
                val b=codec.getOutputBuffer(index)
                if(info.size>0 && started && b!=null) {
                    b.position(info.offset)
                    b.limit(info.offset+info.size)
                    mux.writeSampleData(track,b,info)
                }
                codec.releaseOutputBuffer(index,false)
            }
        }
        return Pair(track,started)
    }

    private fun argbToI420(bm:Bitmap,pixels:IntArray,out:ByteArray) {
        bm.getPixels(pixels,0,W,0,0,W,H)
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
    }
}
