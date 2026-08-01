package com.example.exhibitionarchive.util

import android.media.MediaRecorder
import java.io.File
import javax.inject.Inject

class AudioRecorder @Inject constructor() {
    private var recorder: MediaRecorder? = null

    fun start(file: File) {
        stopSafely()
        val r = MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
    }

    fun stopSafely() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }
}
