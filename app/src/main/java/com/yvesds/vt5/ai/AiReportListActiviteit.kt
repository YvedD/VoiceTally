package com.yvesds.vt5.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activiteit die een lijst toont van dagen waarop geteld is, om een AI-evaluatieverslag te bekijken.
 */
class AiReportListActiviteit : AppCompatActivity() {

    private lateinit var database: VoiceTallyDatabase
    private lateinit var adapter: ReportListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scherm_ai_report_list)

        database = VoiceTallyDatabase.getDatabase(this)
        
        findViewById<View>(R.id.btnTerug).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvReports)
        rv.layoutManager = LinearLayoutManager(this)
        
        adapter = ReportListAdapter { dateMillis ->
            AiEvaluator.showEndOfDayReport(this, dateMillis)
        }
        rv.adapter = adapter

        loadDays()
    }

    private fun loadDays() {
        lifecycleScope.launch(Dispatchers.IO) {
            val headers = database.tellingDao().getAllHeaders()
            
            // Groepeer op dag (timestamp in seconden naar dag-start in millis)
            val days = headers.mapNotNull { 
                it.begintijd.toLongOrNull()?.let { seconds ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = seconds * 1000
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
            }.distinct().sortedDescending()

            withContext(Dispatchers.Main) {
                adapter.submitList(days)
            }
        }
    }

    inner class ReportListAdapter(private val onItemClick: (Long) -> Unit) : RecyclerView.Adapter<ReportListAdapter.ViewHolder>() {
        private var items = listOf<Long>()

        fun submitList(newList: List<Long>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val dateMillis = items[position]
            val sdf = SimpleDateFormat("EEEE d MMMM yyyy", Locale("nl", "BE"))
            val tv = holder.itemView.findViewById<TextView>(android.R.id.text1)
            tv.text = sdf.format(Date(dateMillis))
            tv.setTextColor(android.graphics.Color.WHITE)
            holder.itemView.setOnClickListener { onItemClick(dateMillis) }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v)
    }
}
