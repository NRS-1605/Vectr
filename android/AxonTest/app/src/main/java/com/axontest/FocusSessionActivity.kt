package com.vectr

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class FocusSessionActivity : ComponentActivity() {
    private var voyageId = ""; private var duration = 0; private var started = 0L; private var completing = false
    private lateinit var timer: TextView; private lateinit var detail: TextView
    private val tick = object : Runnable { override fun run() { val left = (duration - ((System.currentTimeMillis() - started) / 1000).toInt()).coerceAtLeast(0); timer.text = "%02d:%02d".format(left / 60, left % 60); if (left == 0) complete() else timer.postDelayed(this, 1000) } }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_focus_session); voyageId = intent.getStringExtra("voyage_id").orEmpty(); duration = intent.getIntExtra("duration", 0); started = intent.getLongExtra("started", System.currentTimeMillis()); timer=findViewById(R.id.focus_timer); detail=findViewById(R.id.focus_detail); window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION; try { startLockTask() } catch (_: Exception) { } ; findViewById<Button>(R.id.focus_end).setOnClickListener { confirmEnd() }; tick.run() }
    private fun complete() { if (completing || voyageId.isBlank()) return; completing=true; detail.text="Saving your completed session…"; VoyageRepository.complete(voyageId, { result -> runOnUiThread { detail.text="Completed · ${BerryFormatter.format(result.optInt("berriesAwarded"))}"; timer.postDelayed({ leaveFocus() }, 1500) } }, { error -> runOnUiThread { completing=false; detail.text=error } }) }
    private fun confirmEnd() { AlertDialog.Builder(this).setTitle("End this focus session?").setMessage("You will lose Berries for ending early.").setNegativeButton("Keep focusing", null).setPositiveButton("End session") { _, _ -> VoyageRepository.abandon(voyageId, { runOnUiThread { leaveFocus() } }, { error -> runOnUiThread { detail.text=error } }) }.show() }
    private fun leaveFocus() { try { stopLockTask() } catch (_: Exception) { }; finish() }
    override fun onBackPressed() { /* The explicit end action prevents accidental exits. */ }
    override fun onDestroy() { timer.removeCallbacks(tick); super.onDestroy() }
}
