package com.yvesds.vt5.features.telling

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.databinding.ItemBirdnetDetectionBinding
import com.yvesds.vt5.features.birdnet.BirdNetDetection

/**
 * RecyclerView-adapter voor BirdNET-GO live-detecties.
 *
 * - Nieuwste items staan bovenaan (prepend via positie 0).
 * - Maximaal [MAX_ITEMS] detecties in geheugen; oudste worden automatisch verwijderd.
 * - Deduplicatie via [BirdNetDetection.deduplicationKey]:
 *     - Zelfde detectie (bijv. bij SSE-reconnect) wordt niet dubbel getoond.
 *     - Twee echte waarnemingen van dezelfde soort op verschillende tijdstippen
 *       blijven als aparte regels zichtbaar.
 *
 * Confidence-kleuring:
 *   ≥ 80 %  → groen   (#00E676)
 *   ≥ 60 %  → geel    (#FFEB3B)
 *   < 60 %  → oranje  (#FF7043)
 */
class BirdNetDetectionAdapter : RecyclerView.Adapter<BirdNetDetectionAdapter.VH>() {

    companion object {
        /** Maximaal aantal detecties dat in de lijst gehouden wordt. */
        const val MAX_ITEMS = 30
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private val items = mutableListOf<BirdNetDetection>()
    private val knownKeys = mutableSetOf<String>()

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    inner class VH(val binding: ItemBirdnetDetectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemBirdnetDetectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val det = items[position]
        with(holder.binding) {
            // Tijdstip — toon enkel HH:mm:ss (take(8) is veilig bij lege string)
            tvBirdTime.text = det.time.take(8)

            // Volksnaam als primaire soortnaam, val back op wetenschappelijke naam
            tvBirdCommonName.text = det.commonName.ifBlank { det.scientificName }
            tvBirdScientificName.text = det.scientificName

            // Confidence als percentage + kleur
            val pct = det.displayConfidencePct
            tvBirdConfidence.text = "$pct%"
            tvBirdConfidence.setTextColor(
                when {
                    pct >= 80 -> Color.parseColor("#00E676")  // felgroen
                    pct >= 60 -> Color.parseColor("#FFEB3B")  // geel
                    else      -> Color.parseColor("#FF7043")  // oranje
                }
            )
        }
    }

    // ─── Publieke mutatie-API ─────────────────────────────────────────────────

    /**
     * Voeg een nieuwe detectie bovenaan de lijst in.
     *
     * Duplicaten (op basis van [BirdNetDetection.deduplicationKey]) worden stilzwijgend
     * genegeerd. Zodra het maximum ([MAX_ITEMS]) bereikt is, wordt het oudste item
     * (onderaan) automatisch verwijderd.
     *
     * Moet aangeroepen worden op de **main thread**.
     */
    fun addDetection(detection: BirdNetDetection) {
        val key = detection.deduplicationKey
        if (!knownKeys.add(key)) return  // duplicaat: negeer

        items.add(0, detection)
        notifyItemInserted(0)

        // Verwijder oudste items boven de limiet
        while (items.size > MAX_ITEMS) {
            val removed = items.removeLast()
            knownKeys.remove(removed.deduplicationKey)
            notifyItemRemoved(items.size) // positie na removeLast()
        }
    }

    /**
     * Wis de volledige lijst (bijv. bij start van een nieuwe tellingssessie
     * of bij het sluiten van het BirdNET-paneel).
     *
     * Moet aangeroepen worden op de **main thread**.
     */
    fun clear() {
        val size = items.size
        items.clear()
        knownKeys.clear()
        notifyItemRangeRemoved(0, size)
    }
}

