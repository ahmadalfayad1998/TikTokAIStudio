package com.example.tiktokai

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

object AacAudioEncoder {
    fun encodeToM4a(input: File, output: File): File {
        if (output.exists()) output.delete()
        val ex = MediaExtractor()
        ex.setDataSource(input.absolutePath)
        val track = findAudioTrack(ex)
        if (track < 0) {
            ex.release()
            throw IllegalStateException("لم يتم العثور على مسار صوت داخل ملف TTS")
        }
        ex.selectTrack(track)
        val src = ex.getTrackFormat(track)
        val sampleRate = src.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = src.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val pcmEncoding = if (src.containsKey(MediaFormat.KEY_PCM_ENCODING))
            src.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
        if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
            ex.release()
            throw IllegalStateException("صيغة PCM غير مدعومة: $pcmEncoding")
        }

        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var outTrack = -1
        var muxStarted = false
        var inputDone = false
        var outputDone = false
        var totalPcmBytes = 0L
        val bytesPerFrame = channels * 2

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        buffer.clear()
                        val size = ex.readSampleData(buffer, 0)
                        if (size < 0) {
                            val pts = totalPcmBytes * 1_000_000L / (sampleRate.toLong() * bytesPerFrame)
                            codec.queueInputBuffer(inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = totalPcmBytes * 1_000_000L / (sampleRate.toLong() * bytesPerFrame)
                            codec.queueInputBuffer(inIndex, 0, size, pts, 0)
                            totalPcmBytes += size
                            ex.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxStarted) throw IllegalStateException("تغيرت صيغة AAC أكثر من مرة")
                        outTrack = mux.addTrack(codec.outputFormat)
                        mux.start()
                        muxStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        if (outBuffer != null && info.size > 0 &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!muxStarted) throw IllegalStateException("لم يبدأ حاوي AAC")
                            outBuffer.position(info.offset)
                            outBuffer.limit(info.offset + info.size)
                            mux.writeSampleData(outTrack, outBuffer, info)
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)
                    }
                }
            }
        } finally {
            ex.release()
            codec.stop()
            codec.release()
            if (muxStarted) mux.stop()
            mux.release()
        }

        if (!output.exists() || output.length() == 0L) {
            throw IllegalStateException("فشل إنشاء ملف AAC")
        }
        return output
    }

    private fun findAudioTrack(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }
}
