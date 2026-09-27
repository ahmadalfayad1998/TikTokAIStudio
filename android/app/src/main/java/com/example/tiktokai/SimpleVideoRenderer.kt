package com.example.tiktokai

import android.content.Context
import android.graphics.*
import android.media.*
import java.io.File
import java.nio.ByteBuffer

object SimpleVideoRenderer {
    private const val W=720
    private const val H=1280
    private const val FPS=15

    fun render(context:Context, scenes:List<String>, durationMs:Long=8000L):File {
        val out=File(context.getExternalFilesDir(null),"tiktok_ai_latest.mp4")
        if(out.exists()) out.delete()
        val safe=if(scenes.isEmpty()) listOf(" ") else scenes
        val frames=((durationMs.coerceIn(3000L,180000L)*FPS+999)/1000).toInt()
        val format=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,W,H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE,2_500_000)
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
                val idx=((frame.toLong()*safe.size)/frames.coerceAtLeast(1)).toInt().coerceIn(0,safe.lastIndex)
                val bitmap=drawFrame(safe[idx],idx,safe.size,frame,frames)
                val yuv=argbToI420(bitmap)
                bitmap.recycle()
                var queued=false
                while(!queued) {
                    val input=codec.dequeueInputBuffer(10_000)
                    if(input>=0) {
                        val b=codec.getInputBuffer(input)!!
                        b.clear(); b.put(yuv)
                        codec.queueInputBuffer(input,0,yuv.size,frame*1_000_000L/FPS,0)
                        queued=true
                    }
                    val state=drain(codec,mux,info,track,started)
                    track=state.first; started=state.second
                }
            }
            var eos=false
            while(!eos) {
                val input=codec.dequeueInputBuffer(10_000)
                if(input>=0) {
                    codec.queueInputBuffer(input,0,0,frames*1_000_000L/FPS,MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    eos=true
                } else {
                    val state=drain(codec,mux,info,track,started);track=state.first;started=state.second
                }
            }
            var done=false
            while(!done) {
                val i=codec.dequeueOutputBuffer(info,10_000)
                if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !started) {
                    track=mux.addTrack(codec.outputFormat);mux.start();started=true
                } else if(i>=0) {
                    val b=codec.getOutputBuffer(i)
                    if(info.size>0&&started&&b!=null) {
                        b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info)
                    }
                    done=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0
                    codec.releaseOutputBuffer(i,false)
                }
            }
        } finally {
            codec.stop();codec.release()
            if(started) mux.stop()
            mux.release()
        }
        return out
    }

    private fun drain(codec:MediaCodec,mux:MediaMuxer,info:MediaCodec.BufferInfo,t0:Int,s0:Boolean):Pair<Int,Boolean>{
        var track=t0;var started=s0
        while(true){
            val i=codec.dequeueOutputBuffer(info,0)
            if(i==MediaCodec.INFO_TRY_AGAIN_LATER) break
            if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED&&!started){track=mux.addTrack(codec.outputFormat);mux.start();started=true}
            else if(i>=0){val b=codec.getOutputBuffer(i);if(info.size>0&&started&&b!=null){b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info)};codec.releaseOutputBuffer(i,false)}
        }
        return Pair(track,started)
    }

    private fun drawFrame(text:String,scene:Int,total:Int,frame:Int,frames:Int):Bitmap {
        val bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888)
        val c=Canvas(bm);c.drawColor(Color.rgb(12,14,24))
        val title=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.LTGRAY;textSize=26f;textAlign=Paint.Align.CENTER}
        val body=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=42f;textAlign=Paint.Align.CENTER;typeface=Typeface.DEFAULT_BOLD}
        c.drawText("مشهد "+(scene+1)+" / "+total,W/2f,120f,title)
        var y=300f
        wrap(text,30).take(9).forEach{c.drawText(it,W/2f,y,body);y+=62f}
        val progress=(frame.toFloat()/frames.coerceAtLeast(1))*(W-120)
        c.drawRect(60f,H-70f,60f+progress,H-58f,title)
        return bm
    }

    private fun argbToI420(bm:Bitmap):ByteArray {
        val pixels=IntArray(W*H);bm.getPixels(pixels,0,W,0,0,W,H)
        val out=ByteArray(W*H*3/2)
        var yi=0;var ui=W*H;var vi=ui+W*H/4
        for(y in 0 until H) for(x in 0 until W) {
            val p=pixels[y*W+x]
            val r=(p shr 16) and 255;val g=(p shr 8) and 255;val b=p and 255
            out[yi++]=(((66*r+129*g+25*b+128 shr 8)+16).coerceIn(0,255)).toByte()
            if(y%2==0&&x%2==0) {
                out[ui++]=(((-38*r-74*g+112*b+128 shr 8)+128).coerceIn(0,255)).toByte()
                out[vi++]=(((112*r-94*g-18*b+128 shr 8)+128).coerceIn(0,255)).toByte()
            }
        }
        return out
    }

    private fun wrap(text:String,n:Int):List<String>{
        val out=mutableListOf<String>();var line=""
        for(w in text.split(Regex("\\s+")).filter{it.isNotBlank()}){
            val next=if(line.isEmpty())w else "$line $w"
            if(next.length>n&&line.isNotEmpty()){out.add(line);line=w}else line=next
        }
        if(line.isNotEmpty())out.add(line);return out
    }
}
