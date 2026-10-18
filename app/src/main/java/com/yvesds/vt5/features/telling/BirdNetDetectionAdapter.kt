package com.yvesds.vt5.features.telling

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yvesds.vt5.databinding.ItemBirdnetDetectionBinding
import com.yvesds.vt5.features.birdnet.BirdNetDetection
import com.yvesds.vt5.features.birdnet.BirdNetSessionStore
import com.yvesds.vt5.features.birdnet.BirdNetSpeciesResolver

/**
 * RecyclerView-adapter voor BirdNET-GO live-detecties.
 * Werkt op zowel smartphone (portrait) als SW600DP tablet schermen.
 *
 * - Nieuwste items worden onderaan toegevoegd (stackFromEnd).
 * - Maximaal [MAX_ITEMS] detecties in geheugen; oudste (bovenaan) worden automatisch verwijderd.
 * - Deduplicatie via [BirdNetDetection.deduplicationKey].
 * - Met [onDetectionAddedToCount] callback wanneer een soort via de checkbox
 *   of door aantikken van de regel wordt toegevoegd aan de telling (finals).
 * - Gesynchroniseerd met [BirdNetSessionStore] zodat detecties bewaard blijven bij schermwissel.
 */
class BirdNetDetectionAdapter : RecyclerView.Adapter<BirdNetDetectionAdapter.VH>() {

    companion object {
        /** Maximaal aantal detecties dat in de lijst gehouden wordt. */
        const val MAX_ITEMS = 30
    }

    // ─── Callbacks ────────────────────────────────────────────────────────────

    /** Callback wanneer de gebruiker de checkbox van een detectie aantikt om op te nemen in de telling. */
    var onDetectionAddedToCount: ((BirdNetDetection) -> Unit)? = null

    // ─── State ────────────────────────────────────────────────────────────────

    private val items = mutableListOf<BirdNetDetection>()
    private val knownKeys = mutableSetOf<String>()
    private val addedKeys = mutableSetOf<String>()

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
        val context = holder.itemView.context
        val dutchName = BirdNetSpeciesResolver.resolveDutchName(
            context = context,
            scientificName = det.scientificName,
            commonName = det.commonName
        )
        val isAlreadyAdded = addedKeys.contains(det.deduplicationKey) || BirdNetSessionStore.isAddedToCount(det.deduplicationKey)

        with(holder.binding) {
            // Tijdstip — toon enkel HH:mm:ss
            tvBirdTime.text = det.time.take(8)

            // Nederlandse soortnaam als primaire weergave
            tvBirdCommonName.text = dutchName
            tvBirdScientificName.text = det.scientificName.ifBlank { det.commonName }

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

            // Checkbox binding om direct op te nemen in de telling
            cbAddToCount.setOnCheckedChangeListener(null)
            cbAddToCount.isChecked = isAlreadyAdded
            cbAddToCount.isEnabled = !isAlreadyAdded

            val toggleAction = {
                if (!addedKeys.contains(det.deduplicationKey) && !BirdNetSessionStore.isAddedToCount(det.deduplicationKey)) {
                    addedKeys.add(det.deduplicationKey)
                    BirdNetSessionStore.markAddedToCount(det.deduplicationKey)
                    cbAddToCount.isChecked = true
                    cbAddToCount.isEnabled = false
                    onDetectionAddedToCount?.invoke(det)
                }
            }

            cbAddToCount.setOnClickListener { toggleAction() }
            root.setOnClickListener { toggleAction() }
        }
    }

    // ─── Publieke mutatie-API ─────────────────────────────────────────────────

    /**
     * Herstelt alle detecties vanuit de persistente [BirdNetSessionStore]
     * (bijvoorbeeld wanneer de gebruiker terugkeert naar TellingScherm).
     */
    fun restoreFromSession() {
        items.clear()
        knownKeys.clear()
        addedKeys.clear()

        val savedDetections = BirdNetSessionStore.getDetections()
        val savedAdded = BirdNetSessionStore.getAddedKeys()

        savedDetections.forEach { det ->
            items.add(det)
            knownKeys.add(det.deduplicationKey)
        }
        addedKeys.addAll(savedAdded)

        notifyDataSetChanged()
    }

    /**
     * Voeg een nieuwe detectie onderaan de lijst toe (nieuwste onderaan).
     */
    fun addDetection(detection: BirdNetDetection) {
        val key = detection.deduplicationKey
        if (!knownKeys.add(key)) return  // duplicaat: negeer

        BirdNetSessionStore.addDetection(detection)

        items.add(detection)
        notifyItemInserted(items.size - 1)

        // Verwijder oudste items (bovenaan) boven de limiet
        while (items.size > MAX_ITEMS) {
            val removed = items.removeAt(0)
            knownKeys.remove(removed.deduplicationKey)
            addedKeys.remove(removed.deduplicationKey)
            notifyItemRemoved(0)
        }
    }

    /**
     * Wis de volledige lijst.
     */
    fun clear() {
        BirdNetSessionStore.clear()
        val size = items.size
        items.clear()
        knownKeys.clear()
        addedKeys.clear()
        notifyItemRangeRemoved(0, size)
    }
}
