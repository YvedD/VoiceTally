package com.yvesds.vt5.core.database.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.TellingHeader
import kotlinx.coroutines.launch

class DatabaseTellingLijstActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_db_telling_lijst)

        database = VoiceTallyDatabase.getDatabase(this)
        recyclerView = findViewById(R.id.rvTellingen)
        recyclerView.layoutManager = LinearLayoutManager(this)

        findViewById<MaterialButton>(R.id.btnTerug).setOnClickListener { finish() }

        loadTellingen()
    }

    private fun loadTellingen() {
        lifecycleScope.launch {
            val tellingen = database.tellingDao().getAllHeaders()
            recyclerView.adapter = TellingAdapter(tellingen) { telling ->
                val intent = Intent(this@DatabaseTellingLijstActiviteit, DatabaseTellingDetailActiviteit::class.java)
                intent.putExtra("tellingid", telling.tellingid)
                startActivity(intent)
            }
        }
    }

    inner class TellingAdapter(
        private val items: List<TellingHeader>,
        private val onClick: (TellingHeader) -> Unit
    ) : RecyclerView.Adapter<TellingAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitel: TextView = view.findViewById(android.R.id.text1)
            val tvSubtitel: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitel.text = "Telling: ${item.tellingid} (${item.onlineid})"
            holder.tvTitel.setTextColor(resources.getColor(R.color.vt5_on_surface))
            holder.tvSubtitel.text = "Start: ${item.begintijd} | Records: ${item.nrec} | Status: ${item.status}"
            holder.tvSubtitel.setTextColor(resources.getColor(R.color.vt5_on_surface))
            holder.tvSubtitel.alpha = 0.7f
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
