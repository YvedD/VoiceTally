package com.yvesds.vt5.hoofd

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.ui.UiColorPrefs

/**
 * InstellingenScherm - Scherm voor app-instellingen
 * 
 * Biedt instellingen voor:
 * - Lettergrootte van logregels (partial/final) in TellingScherm
 * - Lettergrootte van tegels (soortnaam + aantallen) in TellingScherm
 * 
 * Instellingen worden opgeslagen via SharedPreferences voor gebruik doorheen de app.
 */
class InstellingenScherm : AppCompatActivity() {
    
    companion object {
        private const val PREFS_NAME = "vt5_prefs"
        const val PREF_LETTERGROOTTE_LOG_SP = "pref_lettergrootte_log_sp"
        const val PREF_LETTERGROOTTE_TEGELS_SP = "pref_lettergrootte_tegels_sp"
        
        // Lettergrootte bereik in sp
        const val MIN_LETTERGROOTTE_SP = 10
        const val MAX_LETTERGROOTTE_SP = 30
        const val DEFAULT_LETTERGROOTTE_SP = 17
        
        /**
         * Haal de huidige lettergrootte voor logregels op uit SharedPreferences.
         */
        fun getLettergrootteLogSp(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_LETTERGROOTTE_LOG_SP, DEFAULT_LETTERGROOTTE_SP)
        }

        /**
         * Haal de huidige lettergrootte voor tegels op uit SharedPreferences.
         */
        fun getLettergroottTegelsSp(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_LETTERGROOTTE_TEGELS_SP, DEFAULT_LETTERGROOTTE_SP)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_instellingen)

        // Apply user-selected colors to this screen
        UiColorPrefs.applyToActivity(this)

        setupTerugKnop()
        setupLettergrootteNumberPickers()
        setupColorSpinners()
    }
    
    private fun setupTerugKnop() {
        val btnTerug = findViewById<MaterialButton>(R.id.btnTerug)
        btnTerug.setOnClickListener {
            finish()
        }
    }
    
    private fun setupLettergrootteNumberPickers() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // NumberPicker voor logregels
        val npLog = findViewById<NumberPicker>(R.id.npLettergrootteLog)
        npLog.minValue = MIN_LETTERGROOTTE_SP
        npLog.maxValue = MAX_LETTERGROOTTE_SP
        npLog.wrapSelectorWheel = false
        npLog.value = prefs.getInt(PREF_LETTERGROOTTE_LOG_SP, DEFAULT_LETTERGROOTTE_SP)
            .coerceIn(MIN_LETTERGROOTTE_SP, MAX_LETTERGROOTTE_SP)
        
        npLog.setOnValueChangedListener { _, _, newVal ->
            prefs.edit {
                putInt(PREF_LETTERGROOTTE_LOG_SP, newVal)
            }
        }
        
        // NumberPicker voor tegels
        val npTegels = findViewById<NumberPicker>(R.id.npLettergrootteTegels)
        npTegels.minValue = MIN_LETTERGROOTTE_SP
        npTegels.maxValue = MAX_LETTERGROOTTE_SP
        npTegels.wrapSelectorWheel = false
        npTegels.value = prefs.getInt(PREF_LETTERGROOTTE_TEGELS_SP, DEFAULT_LETTERGROOTTE_SP)
            .coerceIn(MIN_LETTERGROOTTE_SP, MAX_LETTERGROOTTE_SP)
        
        npTegels.setOnValueChangedListener { _, _, newVal ->
            prefs.edit {
                putInt(PREF_LETTERGROOTTE_TEGELS_SP, newVal)
            }
        }
    }

    private fun setupColorSpinners() {
        val spBg = findViewById<Spinner>(R.id.spBackgroundColor)
        val spText = findViewById<Spinner>(R.id.spTextColor)

        val bgOptions = UiColorPrefs.backgroundOptions
        val textOptions = UiColorPrefs.textOptions

        fun buildAdapter(
            items: List<UiColorPrefs.ColorOption>,
            getBg: () -> Int,
            getText: () -> Int
        ): BaseAdapter {
            return object : BaseAdapter() {
                override fun getCount(): Int = items.size
                override fun getItem(position: Int): Any = items[position]
                override fun getItemId(position: Int): Long = position.toLong()

                private fun bind(tv: TextView, position: Int) {
                    val opt = items[position]
                    tv.text = opt.label
                    tv.setBackgroundColor(getBg())
                    tv.setTextColor(getText())
                    tv.setPadding(24, 18, 24, 18)
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tv = (convertView as? TextView)
                        ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                    bind(tv, position)
                    return tv
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tv = (convertView as? TextView)
                        ?: layoutInflater.inflate(R.layout.item_color_option, parent, false) as TextView
                    bind(tv, position)
                    return tv
                }
            }
        }

        // Current selection holders (so each spinner can reflect the combined choice)
        var selectedBg = UiColorPrefs.getBackgroundColor(this)
        var selectedText = UiColorPrefs.getTextColor(this)

        val bgAdapter = buildAdapter(bgOptions, getBg = { selectedBg }, getText = { selectedText })
        val textAdapter = buildAdapter(textOptions, getBg = { selectedBg }, getText = { selectedText })

        spBg.adapter = bgAdapter
        spText.adapter = textAdapter

        // Preselect from prefs
        spBg.setSelection(bgOptions.indexOfFirst { it.argb == selectedBg }.takeIf { it >= 0 } ?: 0)
        spText.setSelection(textOptions.indexOfFirst { it.argb == selectedText }.takeIf { it >= 0 } ?: 0)

        spBg.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBg = bgOptions[position].argb
                UiColorPrefs.setBackgroundColor(this@InstellingenScherm, selectedBg)

                // Refresh both spinners so each item reflects the combined colors
                bgAdapter.notifyDataSetChanged()
                textAdapter.notifyDataSetChanged()

                UiColorPrefs.applyToActivity(this@InstellingenScherm)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spText.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedText = textOptions[position].argb
                UiColorPrefs.setTextColor(this@InstellingenScherm, selectedText)

                // Refresh both spinners so each item reflects the combined colors
                bgAdapter.notifyDataSetChanged()
                textAdapter.notifyDataSetChanged()

                UiColorPrefs.applyToActivity(this@InstellingenScherm)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
}
