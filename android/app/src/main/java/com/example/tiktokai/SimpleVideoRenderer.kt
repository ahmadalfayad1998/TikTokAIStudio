package com.example.tiktokai
import android.content.Context
import android.graphics.*
import android.media.*
import android.view.Surface
import java.io.File

object SimpleVideoRenderer {
 private const val W=720; private const val H=1280; private const val FPS=30
 fun render(context:Context,scenes:List<String>,durationMs:Long=8000L):File {
  val out=File(context.getExternalFilesDir(null),"tiktok_ai_latest.mp4"); if(out.exists()) out.delete()
  val fmt=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,W,H).apply {
   setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
   setInteger(MediaFormat.KEY_BIT_RATE,3500000); setInteger(MediaFormat.KEY_FRAME_RATE,FPS); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)
  }
  val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC); codec.configure(fmt,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE)
  val surface=codec.createInputSurface(); codec.start()
  val mux=MediaMuxer(out.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); val info=MediaCodec.BufferInfo()
  var track=-1; var started=false
  try {
   val safeScenes=if(scenes.isEmpty()) listOf(" ") else scenes
   val seconds=(durationMs.coerceIn(3000L,180000L)+999L)/1000L
   for(frame in 0 until (FPS*seconds).toInt()) {
    val totalFrames=(FPS*seconds).toInt().coerceAtLeast(1)
    val sceneIndex=((frame.toLong()*safeScenes.size)/totalFrames).toInt().coerceIn(0,safeScenes.lastIndex)
    val lines=wrap(safeScenes[sceneIndex].replace("\n"," "),30).take(9)
    draw(surface,lines,frame,totalFrames,sceneIndex,safeScenes.size); while(true) {
    val i=codec.dequeueOutputBuffer(info,0)
    if(i==MediaCodec.INFO_TRY_AGAIN_LATER) break
    if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track=mux.addTrack(codec.outputFormat); mux.start(); started=true }
    else if(i>=0) { val b=codec.getOutputBuffer(i); if(info.size>0&&started&&b!=null){b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info)}; codec.releaseOutputBuffer(i,false) }
   }}
   codec.signalEndOfInputStream(); var done=false
   while(!done){ val i=codec.dequeueOutputBuffer(info,10000); if(i==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED&&track<0){track=mux.addTrack(codec.outputFormat);mux.start();started=true}
    else if(i>=0){val b=codec.getOutputBuffer(i);if(info.size>0&&started&&b!=null){b.position(info.offset);b.limit(info.offset+info.size);mux.writeSampleData(track,b,info)};done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(i,false)}
   }
  } finally { codec.stop();codec.release();if(started)mux.stop();mux.release();surface.release() }
  return out
 }
 private fun draw(surface:Surface,lines:List<String>,frame:Int,totalFrames:Int,scene:Int,total:Int){val c=surface.lockCanvas(null);try{c.drawColor(Color.rgb(12,14,24))
  val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=42f;textAlign=Paint.Align.CENTER;typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)}
  val s=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.LTGRAY;textSize=26f;textAlign=Paint.Align.CENTER}
  c.drawText("مشهد "+(scene+1)+" / "+total,W/2f,120f,s);var y=300f;lines.forEach{c.drawText(it,W/2f,y,p);y+=62f};val progress=((frame.toFloat()/totalFrames.coerceAtLeast(1)).coerceIn(0f,1f))*(W-120);c.drawRect(60f,H-70f,60f+progress,H-58f,s)
 }finally{surface.unlockCanvasAndPost(c)}}
 private fun wrap(text:String,n:Int):List<String>{val out=mutableListOf<String>();var line="";for(w in text.split(Regex("\\s+")).filter{it.isNotBlank()}){val next=if(line.isEmpty())w else line+" "+w;if(next.length>n&&line.isNotEmpty()){out.add(line);line=w}else line=next};if(line.isNotEmpty())out.add(line);return out}
}
