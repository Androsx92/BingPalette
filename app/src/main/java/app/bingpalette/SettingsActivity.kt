package app.bingpalette

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var timeButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences(PaletteService.PREFS, MODE_PRIVATE)

        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }

        // Schedule
        val autoSwitch = findViewById<MaterialSwitch>(R.id.autoSwitch)
        autoSwitch.isChecked = prefs.getBoolean("auto", true)
        autoSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("auto", on).apply() }

        timeButton = findViewById(R.id.timeButton)
        updateTimeText()
        timeButton.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(prefs.getInt("hour", 5))
                .setMinute(prefs.getInt("minute", 0))
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .setTitleText("Start after")
                .build()
            picker.addOnPositiveButtonClickListener {
                prefs.edit().putInt("hour", picker.hour).putInt("minute", picker.minute).apply()
                updateTimeText()
            }
            picker.show(supportFragmentManager, "time")
        }

        // Market dropdown
        val drop = findViewById<MaterialAutoCompleteTextView>(R.id.marketDrop)
        drop.setSimpleItems(Markets.codes.map { Markets.label(it) }.toTypedArray())
        drop.setText(Markets.label(prefs.getString("mkt", "de-DE") ?: "de-DE"), false)
        drop.setOnItemClickListener { _, _, position, _ ->
            prefs.edit().putString("mkt", Markets.codes[position]).apply()
        }

        // Chips
        bindChips(R.id.targetGroup, "target", listOf(R.id.chipTargetBoth, R.id.chipTargetHome, R.id.chipTargetLock))
        bindChips(R.id.screensGroup, "screens", listOf(R.id.chipScreensBoth, R.id.chipScreensInner, R.id.chipScreensCover))

        val paletteSwitch = findViewById<MaterialSwitch>(R.id.paletteSwitch)
        paletteSwitch.isChecked = prefs.getBoolean("palette", true)
        paletteSwitch.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("palette", on).apply() }
    }

    private fun updateTimeText() {
        timeButton.text = String.format(Locale.US, "%02d:%02d", prefs.getInt("hour", 5), prefs.getInt("minute", 0))
    }

    private fun bindChips(groupId: Int, key: String, ids: List<Int>) {
        val group = findViewById<ChipGroup>(groupId)
        group.check(ids[prefs.getInt(key, 0).coerceIn(0, ids.size - 1)])
        group.setOnCheckedStateChangeListener { _, checked ->
            val i = ids.indexOf(checked.firstOrNull())
            if (i >= 0) prefs.edit().putInt(key, i).apply()
        }
    }
}
