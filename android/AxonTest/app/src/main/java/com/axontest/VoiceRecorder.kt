package com.vectr

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

class VoiceRecorder(private val outputFile: File) {
    private val sampleRate = 16_000
    private val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var recording = false

    fun start() {
        if (minBuffer <= 0) throw IllegalStateException("This device does not support 16 kHz mono recording")
        outputFile.parentFile?.mkdirs()
        val audio = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        if (audio.state != AudioRecord.STATE_INITIALIZED) { audio.release(); throw IllegalStateException("Could not initialize microphone") }
        recorder = audio
        recording = true
        audio.startRecording()
        worker = thread(name = "vectr-voice-recording") {
            outputFile.outputStream().buffered().use { output ->
                output.write(ByteArray(44))
                val buffer = ByteArray(minBuffer)
                var bytesWritten = 0L
                while (recording) {
                    val read = audio.read(buffer, 0, buffer.size)
                    if (read > 0) { output.write(buffer, 0, read); bytesWritten += read }
                }
                output.flush()
                writeWavHeader(outputFile, bytesWritten)
            }
        }
    }

    fun stop(): File {
        recording = false
        recorder?.runCatching { stop() }
        worker?.join(2_000)
        recorder?.release()
        recorder = null
        if (outputFile.length() <= 44) throw IllegalStateException("The recording was empty")
        return outputFile
    }

    private fun writeWavHeader(file: File, pcmBytes: Long) {
        RandomAccessFile(file, "rw").use { wav ->
            val header = ByteArray(44)
            fun putText(offset: Int, text: String) = text.toByteArray().copyInto(header, offset)
            fun putInt(offset: Int, value: Int) { header[offset] = value.toByte(); header[offset + 1] = (value shr 8).toByte(); header[offset + 2] = (value shr 16).toByte(); header[offset + 3] = (value shr 24).toByte() }
            fun putShort(offset: Int, value: Int) { header[offset] = value.toByte(); header[offset + 1] = (value shr 8).toByte() }
            putText(0, "RIFF"); putInt(4, (36 + pcmBytes).toInt()); putText(8, "WAVEfmt ")
            putInt(16, 16); putShort(20, 1); putShort(22, 1); putInt(24, sampleRate); putInt(28, sampleRate * 2); putShort(32, 2); putShort(34, 16)
            putText(36, "data"); putInt(40, pcmBytes.toInt())
            wav.seek(0); wav.write(header)
        }
    }
}
