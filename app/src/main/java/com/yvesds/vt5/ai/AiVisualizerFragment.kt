package com.yvesds.vt5.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yvesds.vt5.R

class AiVisualizerFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_ai_visualizer, container, false)
        val tv = v.findViewById<TextView>(R.id.tvAiContent)
        val btn = v.findViewById<Button>(R.id.btnAiFeedback)

        btn.setOnClickListener {
            // placeholder: open feedback dialog / send to Trainer
            tv.text = "Feedback gestuurd (lokale update volgt)"
        }

        return v
    }
}

