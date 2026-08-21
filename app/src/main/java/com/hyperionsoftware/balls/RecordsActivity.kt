package com.hyperionsoftware.balls

import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.hyperionsoftware.balls.databinding.ActivityRecordsBinding
import com.hyperionsoftware.balls.records.PersonalRecords
import com.hyperionsoftware.balls.records.RecordType

// A little "career" leaderboard against yourself - lists every RecordType with whatever
// personal best has been set for it so far (0 if never), refreshed on every visit rather than
// only in onCreate so returning here right after a match shows its result immediately.
class RecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        binding.recordsContainer.removeAllViews()
        RecordType.entries.forEach { type ->
            binding.recordsContainer.addView(recordRow(type))
        }
    }

    private fun recordRow(type: RecordType): LinearLayout {
        val value = PersonalRecords.get(this, type)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            addView(TextView(this@RecordsActivity).apply {
                text = getString(type.titleResId)
                setTextColor(getColor(R.color.hud_text))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                alpha = 0.9f
            })
            addView(TextView(this@RecordsActivity).apply {
                text = PersonalRecords.formatValue(this@RecordsActivity, type, value)
                setTextColor(getColor(R.color.player_color))
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
            })
        }
    }
}
