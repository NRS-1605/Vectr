package com.vectr

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.DateFormat
import java.util.Date

class CaptureOverlayActivity : Activity() {
    private lateinit var headingText: EditText
    private lateinit var tagText: AutoCompleteTextView
    private lateinit var bodyText: EditText
    private var screenshotFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        setContentView(R.layout.activity_capture_overlay)
        headingText = findViewById(R.id.capture_heading); tagText = findViewById(R.id.capture_tag); bodyText = findViewById(R.id.capture_text)
        screenshotFile = intent.getStringExtra(EXTRA_SCREENSHOT_PATH)?.let(::File)?.takeIf(File::exists)
        screenshotFile?.let { file -> findViewById<ImageView>(R.id.capture_screenshot_preview).apply { setImageBitmap(BitmapFactory.decodeFile(file.absolutePath)); visibility = View.VISIBLE } }
        loadTagSuggestions(); bindVoiceInput()
        findViewById<ImageButton>(R.id.gallery_button).setOnClickListener { openPhotoAlbum() }
        findViewById<View>(R.id.capture_submit).setOnClickListener { submitCapture() }
        headingText.requestFocus()
    }

    private fun bindVoiceInput() {
        val mic = findViewById<ImageButton>(R.id.mic_button); val indicator = findViewById<TextView>(R.id.voice_recording_indicator)
        val wave = findViewById<android.widget.LinearLayout>(R.id.recording_wave)
        var recorder: VoiceRecorder? = null; var startedAt = 0L
        val send = findViewById<View>(R.id.capture_submit)
        val gallery = findViewById<ImageButton>(R.id.gallery_button)
        val pulse = AnimatorSet().apply { playTogether((0 until wave.childCount).map { index -> ObjectAnimator.ofFloat(wave.getChildAt(index), View.SCALE_Y, .45f, 1.35f, .45f).apply { duration = 520; startDelay = index * 90L; repeatCount = ObjectAnimator.INFINITE } }) }
        lateinit var ticker: Runnable
        ticker = Runnable { if (recorder != null) { val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt(); indicator.text = "● Recording ${elapsed / 60}:${(elapsed % 60).toString().padStart(2, '0')}"; indicator.postDelayed(ticker, 500) } }
        mic.setOnClickListener {
            if (recorder == null) {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC); return@setOnClickListener }
                try {
                    recorder = VoiceRecorder(File(filesDir, "voice-recordings/${System.currentTimeMillis()}.wav")).also { it.start() }; startedAt = System.currentTimeMillis()
                    indicator.visibility = View.VISIBLE; wave.visibility = View.VISIBLE; send.visibility = View.GONE; gallery.visibility = View.GONE; pulse.start(); ticker.run()
                } catch (error: Exception) { Toast.makeText(this, error.message ?: "Could not start recording", Toast.LENGTH_LONG).show() }
            } else {
                val active = recorder ?: return@setOnClickListener; recorder = null; indicator.removeCallbacks(ticker); pulse.cancel(); indicator.visibility = View.GONE; wave.visibility = View.GONE; send.visibility = View.VISIBLE; gallery.visibility = View.VISIBLE
                try {
                    val voiceFile = active.stop()
                    CaptureRepository.uploadVoiceFile(voiceFile, saveCapture = false,
                        onSuccess = { result -> runOnUiThread { voiceFile.delete(); if (headingText.text.isBlank()) headingText.setText(result.heading); tagText.setText("voice", false); bodyText.setText(result.transcript) } },
                        onError = { error -> runOnUiThread { OfflineCaptureQueue.enqueue(this, "Voice ${DateFormat.getDateTimeInstance().format(Date())}", "voice", "", voicePath = voiceFile.absolutePath); Toast.makeText(this, "$error. Recording queued for retry.", Toast.LENGTH_LONG).show() } },
                    )
                } catch (error: Exception) { Toast.makeText(this, error.message ?: "Could not stop recording", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun submitCapture() {
        val heading = headingText.text.toString(); val tag = tagText.text.toString(); val body = bodyText.text.toString()
        if (heading.isBlank() || tag.isBlank() || body.isBlank()) { Toast.makeText(this, R.string.capture_details_required, Toast.LENGTH_SHORT).show(); return }
        fun send(imageFilename: String? = null) = DeviceWebSocket.sendDeviceCapture(heading, tag, body, imageFilename, onSent = { runOnUiThread { screenshotFile?.delete(); finish() } }, onError = { error -> runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_LONG).show() } })
        val image = screenshotFile
        if (!DeviceWebSocket.isConnected()) {
            OfflineCaptureQueue.enqueue(this, heading, tag, body, image?.let { BitmapFactory.decodeFile(it.absolutePath) }); image?.delete(); finish(); return
        }
        if (image != null && image.exists()) CaptureRepository.uploadImageFile(image, onSuccess = { filename -> send(filename) }, onError = { error -> runOnUiThread { Toast.makeText(this, error, Toast.LENGTH_LONG).show() } }) else send()
    }

    private fun loadTagSuggestions() = CaptureRepository.fetchTags(
        onSuccess = { tags -> runOnUiThread { val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tags); tagText.setAdapter(adapter); tagText.threshold = 0; tagText.setOnClickListener { if (adapter.count > 0) tagText.showDropDown() } } },
        onError = { },
    )
    private fun openPhotoAlbum() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, REQUEST_PHOTO)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PHOTO || resultCode != RESULT_OK) return
        val bitmap = data?.data?.let { uri -> contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) } ?: return
        screenshotFile?.delete()
        screenshotFile = File(cacheDir, "quick-captures/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs(); outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) } }
        findViewById<ImageView>(R.id.capture_screenshot_preview).apply { setImageBitmap(bitmap); visibility = View.VISIBLE }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); if (requestCode == REQUEST_MIC && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) Toast.makeText(this, R.string.microphone_permission_denied, Toast.LENGTH_LONG).show() }
    companion object { const val EXTRA_SCREENSHOT_PATH = "screenshotPath"; private const val REQUEST_MIC = 92; private const val REQUEST_PHOTO = 93 }
}
