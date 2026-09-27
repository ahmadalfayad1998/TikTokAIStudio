package com.example.tiktokai

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object AudioVideoMuxer {
    fun mux(video: File, audio: File, output: File): File {
        if (output.exists()) output.delete()
        val videoEx = MediaExtractor()
        val encodedAudio = File(output.parentFile, "tiktok_ai_voice_aac.m4a")
        val aac = AacAudioEncoder.encodeToM4a(audio, encodedAudio)
        val audioEx = MediaExtractor()
        videoEx.setDataSource(video.absolutePath)
        audioEx.setDataSource(aac.absolutePath)

        val videoTrack = findTrack(videoEx, "video/")
        val audioTrack = findTrack(audioEx, "audio/")
        if (videoTrack < 0) throw IllegalStateException("لم يتم العثور على مسار الفيديو")
        if (audioTrack < 0) throw IllegalStateException("لم يتم العثور على مسار الصوت")

        videoEx.selectTrack(videoTrack)
        audioEx.selectTrack(audioTrack)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outVideo = muxer.addTrack(videoEx.getTrackFormat(videoTrack))
        val outAudio = muxer.addTrack(audioEx.getTrackFormat(audioTrack))
        muxer.start()

        try {
            copy(videoEx, muxer, outVideo)
            copy(audioEx, muxer, outAudio)
        } finally {
            muxer.stop()
            muxer.release()
            videoEx.release()
            audioEx.release()
            if (encodedAudio.exists()) encodedAudio.delete()
        }
        return output
    }

    private fun findTrack(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    private fun copy(ex: MediaExtractor, mux: MediaMuxer, track: Int) {
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = android.media.MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = ex.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = ex.sampleTime
            info.flags = ex.sampleFlags
            mux.writeSampleData(track, buffer, info)
            ex.advance()
        }
    }
}
