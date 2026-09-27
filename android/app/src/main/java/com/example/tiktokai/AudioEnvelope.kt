package com.example.tiktokai

import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioEnvelope private constructor(
    private val timesUs:LongArray,
    private val values:FloatArray
) {
    fun amplitudeAt(timeUs:Long):Float {
        if(timesUs.isEmpty() || values.isEmpty()) return 0.32f
        var low=0
        var high=timesUs.lastIndex
        while(low<=high) {
            val mid=(low+high) ushr 1
            val t=timesUs[mid]
            when {
                t<timeUs -> low=mid+1
                t>timeUs -> high=mid-1
                else -> return values[mid]
            }
        }
        val index=low.coerceIn(0,values.lastIndex)
        val prev=(index-1).coerceAtLeast(0)
        return if(index==prev) values[index] else {
            val left=timesUs[prev]
            val right=timesUs[index]
            if(right<=left) values[index] else {
                val f=((timeUs-left).toDouble()/(right-left).toDouble()).coerceIn(0.0,1.0).toFloat()
                values[prev]*(1f-f)+values[index]*f
            }
        }
    }

    companion object {
        fun fromAudioFile(file:File):AudioEnvelope {
            val extractor=MediaExtractor()
            extractor.setDataSource(file.absolutePath)
            val track=(0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            } ?: run {
                extractor.release()
                return AudioEnvelope(longArrayOf(),floatArrayOf())
            }

            extractor.selectTrack(track)
            val format=extractor.getTrackFormat(track)
            val sampleRate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val pcm=if(format.containsKey(MediaFormat.KEY_PCM_ENCODING))
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            else AudioFormat.ENCODING_PCM_16BIT

            if(pcm!=AudioFormat.ENCODING_PCM_16BIT || sampleRate<=0 || channels<=0) {
                extractor.release()
                return AudioEnvelope(longArrayOf(),floatArrayOf())
            }

            val bytesPerFrame=channels*2
            val buffer=ByteBuffer.allocate(64*1024).order(ByteOrder.LITTLE_ENDIAN)
            val timeList=mutableListOf<Long>()
            val valueList=mutableListOf<Float>()
            var totalBytes=0L
            var maxValue=1f

            try {
                while(true) {
                    buffer.clear()
                    val size=extractor.readSampleData(buffer,0)
                    if(size<0) break
                    var sum=0L
                    var count=0
                    var i=0
                    while(i+1<size) {
                        val lo=buffer.get(i).toInt() and 0xff
                        val hi=buffer.get(i+1).toInt()
                        val sample=((hi shl 8) or lo).toShort().toInt()
                        sum+=abs(sample).toLong()
                        count++
                        i+=2
                    }
                    val avg=if(count==0) 0f else sum.toFloat()/count.toFloat()
                    val time=totalBytes*1_000_000L/(sampleRate.toLong()*bytesPerFrame.toLong())
                    timeList.add(time)
                    valueList.add(avg)
                    if(avg>maxValue) maxValue=avg
                    totalBytes+=size
                    extractor.advance()
                }
            } finally {
                extractor.release()
            }

            val normalized=FloatArray(valueList.size) { idx ->
                val raw=(valueList[idx]/maxValue).coerceIn(0f,1f)
                // Preserve some mouth movement even with quiet speech.
                (raw*0.92f).coerceIn(0.03f,1f)
            }
            return AudioEnvelope(timeList.toLongArray(),normalized)
        }
    }
}
