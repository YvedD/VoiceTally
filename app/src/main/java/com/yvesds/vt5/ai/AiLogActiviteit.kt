package com.yvesds.vt5.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.entities.AiLog
import com.yvesds.vt5.core.database.ui.SpeciesNameResolver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activiteit voor het bekijken van de AI-historiek en het geven van feedback.
 */
class AiLogActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var adapter: AiLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_logs)

        database = VoiceTallyDatabase.getDatabase(this)
        
        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvAiLogs)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = AiLogAdapter { log ->
            showFeedbackDialog(log)
        }
        rv.adapter = adapter

        observeLogs()
    }

    private fun observeLogs() {
        lifecycleScope.launch {
            database.tellingDao().getAllAiLogsFlow().collectLatest { logs ->
                adapter.submitList(logs)
            }
        }
    }

    private fun showFeedbackDialog(log: AiLog) {
        AiFeedbackDialoog.show(this, log.tellingid, logId = log.id) {
            // Updated in DB via the dialog
        }
    }

    inner class AiLogAdapter(private val onItemClick: (AiLog) -> Unit) : RecyclerView.Adapter<AiLogAdapter.ViewHolder>() {
        private var items = listOf<AiLog>()

        fun submitList(newList: List<AiLog>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_ai_log, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val sdf = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
            holder.tvDate.text = "${sdf.format(Date(item.timestamp))} (Sessie: ${item.tellingid})"
            holder.rbRating.rating = item.rating.toFloat()
            
            // Parse details from context/suggestions JSON
            try {
                val suggestions = org.json.JSONObject(item.suggestions).optJSONArray("items")
                val names = mutableListOf<String>()
                if (suggestions != null) {
                    for (i in 0 until minOf(suggestions.length(), 3)) {
                        val s = suggestions.getJSONObject(i)
                        names.add("${s.getString("name")} (${s.getInt("prob")}%)")
                    }
                }
                holder.tvDetails.text = "Suggesties: ${names.joinToString(", ")}"
            } catch (_: Exception) {
                holder.tvDetails.text = "Type: ${item.type}"
            }

            if (item.feedback.isNotBlank()) {
                holder.tvComment.text = "Feedback: ${item.feedback}"
                holder.tvComment.visibility = View.VISIBLE
            } else {
                holder.tvComment.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvDate = v.findViewById<TextView>(R.id.tvLogDate)
            val rbRating = v.findViewById<RatingBar>(R.id.rbLogRating)
            val tvDetails = v.findViewById<TextView>(R.id.tvLogDetails)
            val tvComment = v.findViewById<TextView>(R.id.tvLogComment)
        }
    }
}
