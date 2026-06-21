package com.yvesds.vt5.features.telling.ui.records

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.yvesds.vt5.R
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.databinding.SchermRoomUiBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment voor het beheren van records. Kan worden getoond als DialogFragment.
 */
@AndroidEntryPoint
class RecordManagerFragment : DialogFragment() {

    private var _binding: SchermRoomUiBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordManagerViewModel by viewModels()
    private lateinit var adapter: RecordAdapter

    companion object {
        private const val ARG_TELLING_ID = "telling_id"

        fun newInstance(tellingId: String): RecordManagerFragment {
            return RecordManagerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TELLING_ID, tellingId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Optioneel: Stijl instellen voor DialogFragment
        setStyle(STYLE_NORMAL, R.style.Theme_MaterialComponents_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SchermRoomUiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tellingId = arguments?.getString(ARG_TELLING_ID)
        if (tellingId == null) {
            dismiss()
            return
        }
        viewModel.setTellingId(tellingId)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupBatchActions()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        adapter = RecordAdapter(
            onEdit = { observation ->
                // To-be implemented: Open edit dialog
                Toast.makeText(requireContext(), "Bewerken: ${observation.speciesId}", Toast.LENGTH_SHORT).show()
            },
            onDelete = { observation ->
                showSingleDeleteConfirmation(observation)
            },
            onSelectionChanged = { observation, isChecked ->
                viewModel.toggleSelection(observation.id)
            }
        )
        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }
    }

    private fun setupBatchActions() {
        binding.btnBatchDelete.setOnClickListener {
            showBatchDeleteConfirmation()
        }
        binding.btnBatchCancel.setOnClickListener {
            viewModel.clearSelection()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observeer de lijst met waarnemingen
                launch {
                    viewModel.observations.collect { list ->
                        adapter.submitList(list)
                    }
                }

                // Observeer geselecteerde IDs voor UI updates
                launch {
                    viewModel.selectedIds.collect { selectedIds ->
                        adapter.setSelectedIds(selectedIds)
                        updateBatchBar(selectedIds.size)
                    }
                }
            }
        }
    }

    private fun updateBatchBar(selectedCount: Int) {
        binding.cardBatchActions.visibility = if (selectedCount > 0) View.VISIBLE else View.GONE
        binding.tvBatchInfo.text = getString(R.string.room_ui_batch_info, selectedCount)
    }

    private fun showSingleDeleteConfirmation(observation: ObservationEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.beheer_record_verwijderen)
            .setMessage(getString(R.string.beheer_record_verwijder_msg, 0, observation.speciesId)) // Index 0 is dummy hier
            .setPositiveButton(R.string.beheer_verwijderen) { _, _ ->
                viewModel.deleteObservation(observation)
            }
            .setNegativeButton(R.string.annuleer, null)
            .show()
    }

    private fun showBatchDeleteConfirmation() {
        val count = viewModel.selectedIds.value.size
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.room_ui_delete_confirm_title)
            .setMessage(getString(R.string.room_ui_delete_confirm_msg, count))
            .setPositiveButton(R.string.room_ui_batch_delete) { _, _ ->
                viewModel.deleteSelectedRecords()
            }
            .setNegativeButton(R.string.annuleer, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
