package com.example.tiktokai

import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioEnvelope private constructor(
    private val timesUs:LongArray,
    private val values:FloatArray
) {
    fun amplitudeAt(timeUs:Long):Float {
        if(timesUs.isEmpty() || values.isEmpty()) return 0.25f
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
        if(index==prev) return values[index]
        val left=timesUs[prev]
        val right=timesUs[index]
        if(right<=left) return values[index]
        val f=((timeUs-left).toDouble()/(right-left).toDouble()).coerceIn(0.0,1.0).toFloat()
        return values[prev]*(1f-f)+values[index]*f
    }

    companion object {
        fun fromAudioFile(file:File):AudioEnvelope {
            readWavEnvelope(file)?.let { return it }
            return readExtractorEnvelope(file)
        }

        private fun readWavEnvelope(file:File):AudioEnvelope? {
            return runCatching {
                RandomAccessFile(file,"r").use { raf ->
                    if(raf.length()<44L) return@use null
                    val riff=ByteArray(4)
                    raf.readFully(riff)
                    if(String(riff,Charsets.US_ASCII)!="RIFF") return@use null
                    readLeInt(raf) // RIFF size
                    val wave=ByteArray(4)
                    raf.readFully(wave)
                    if(String(wave,Charsets.US_ASCII)!="WAVE") return@use null

                    var audioFormat=0
                    var channels=0
                    var sampleRate=0
                    var bitsPerSample=0
                    var dataOffset=-1L
                    var dataSize=0L

                    while(raf.filePointer+8<=raf.length()) {
                        val idBytes=ByteArray(4)
                        raf.readFully(idBytes)
                        val id=String(idBytes,Charsets.US_ASCII)
                        val size=(readLeInt(raf).toLong() and 0xffffffffL)
                        val start=raf.filePointer

                        when(id) {
                            "fmt " -> {
                                if(size>=16) {
                                    audioFormat=readLeShort(raf)
                                    channels=readLeShort(raf)
                                    sampleRate=readLeInt(raf)
                                    readLeInt(raf) // byteRate
                                    readLeShort(raf) // blockAlign
                                    bitsPerSample=readLeShort(raf)
                                }
                            }
                            "data" -> {
                                dataOffset=start
                                dataSize=size
                            }
                        }

                        val padded=size+(size and 1L)
                        val next=start+padded
                        if(next>raf.length()) break
                        raf.seek(next)
                        if(dataOffset>=0 && sampleRate>0 && channels>0 && bitsPerSample>0) break
                    }

                    if(audioFormat!=1 || bitsPerSample!=16 || channels<=0 || sampleRate<=0 || dataOffset<0) {
                        return@use null
                    }

                    val bytesPerFrame=channels*2
                    val windowFrames=(sampleRate/40).coerceAtLeast(1) // ~25 ms
                    val windowBytes=(windowFrames*bytesPerFrame).coerceAtLeast(bytesPerFrame)
                    val buffer=ByteArray(windowBytes)
                    val times=mutableListOf<Long>()
                    val values=mutableListOf<Float>()
                    var processedBytes=0L
                    var maxValue=1f

                    raf.seek(dataOffset)
                    val available=minOf(dataSize,raf.length()-dataOffset)
                    while(processedBytes<available) {
                        val wanted=minOf(buffer.size.toLong(),available-processedBytes).toInt()
                        val read=raf.read(buffer,0,wanted)
                        if(read<=0) break

                        var sum=0L
                        var count=0
                        var i=0
                        while(i+1<read) {
                            val lo=buffer[i].toInt() and 0xff
                            val hi=buffer[i+1].toInt()
                            val sample=((hi shl 8) or lo).toShort().toInt()
                            sum+=abs(sample).toLong()
                            count++
                            i+=2
                        }
                        val avg=if(count==0) 0f else sum.toFloat()/count.toFloat()
                        val time=processedBytes*1_000_000L/(sampleRate.toLong()*bytesPerFrame.toLong())
                        times.add(time)
                        values.add(avg)
                        if(avg>maxValue) maxValue=avg
                        processedBytes+=read
                    }
                    normalized(times,values,maxValue)
                }
            }.getOrNull()
        }

        private fun readExtractorEnvelope(file:File):AudioEnvelope {
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
            val times=mutableListOf<Long>()
            val values=mutableListOf<Float>()
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
                    times.add(time)
                    values.add(avg)
                    if(avg>maxValue) maxValue=avg
                    totalBytes+=size
                    extractor.advance()
                }
            } finally {
                extractor.release()
            }
            return normalized(times,values,maxValue)
        }

        private fun normalized(times:List<Long>,values:List<Float>,maxValue:Float):AudioEnvelope {
            if(values.isEmpty()) return AudioEnvelope(longArrayOf(),floatArrayOf())
            val safeMax=maxValue.coerceAtLeast(1f)
            val normalized=FloatArray(values.size) { idx ->
                val raw=(values[idx]/safeMax).coerceIn(0f,1f)
                // Compress the range so quiet syllables still move the mouth,
                // while silence remains visually near closed.
                when {
                    raw<0.035f -> 0.02f
                    else -> (0.08f+raw*0.92f).coerceIn(0f,1f)
                }
            }
            return AudioEnvelope(times.toLongArray(),normalized)
        }

        private fun readLeShort(raf:RandomAccessFile):Int {
            val a=raf.read()
            val b=raf.read()
            if(a<0 || b<0) throw IllegalStateException("Unexpected WAV EOF")
            return a or (b shl 8)
        }

        private fun readLeInt(raf:RandomAccessFile):Int {
            val a=raf.read()
            val b=raf.read()
            val c=raf.read()
            val d=raf.read()
            if(a<0 || b<0 || c<0 || d<0) throw IllegalStateException("Unexpected WAV EOF")
            return a or (b shl 8) or (c shl 16) or (d shl 24)
        }
    }
}
