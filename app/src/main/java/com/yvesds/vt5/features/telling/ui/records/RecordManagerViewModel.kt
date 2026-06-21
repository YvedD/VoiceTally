package com.yvesds.vt5.features.telling.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.vt5.core.database.entity.ObservationEntity
import com.yvesds.vt5.features.telling.data.TellingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel voor het beheren van waarnemingen binnen een specifieke telling.
 * Verwerkt filters, zoekopdrachten en batch-selecties.
 */
@HiltViewModel
class RecordManagerViewModel @Inject constructor(
    private val repository: TellingRepository
) : ViewModel() {

    private val _tellingId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    
    // Bijhouden van geselecteerde record IDs voor batch-operaties
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds

    /**
     * Initialiseert de ViewModel met een specifieke tellingId.
     */
    fun setTellingId(id: String) {
        _tellingId.value = id
    }

    /**
     * Bijwerken van de zoekopdracht.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Schakelt de selectie van een record aan of uit.
     */
    fun toggleSelection(id: String) {
        val current = _selectedIds.value
        if (current.contains(id)) {
            _selectedIds.value = current - id
        } else {
            _selectedIds.value = current + id
        }
    }

    /**
     * Reset de volledige selectie.
     */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Gefilterde lijst van waarnemingen op basis van tellingId en zoekopdracht.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val observations: StateFlow<List<ObservationEntity>> = combine(
        _tellingId,
        _searchQuery
    ) { id, query -> id to query }
        .flatMapLatest { (id, query) ->
            if (id == null) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                repository.getFilteredObservationsFlow(id, query)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Verwijder geselecteerde records in batch.
     */
    fun deleteSelectedRecords() {
        val idsToDelete = _selectedIds.value.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            repository.deleteObservationsByIds(idsToDelete)
            clearSelection()
        }
    }

    /**
     * Verwijder een enkele waarneming.
     */
    fun deleteObservation(observation: ObservationEntity) {
        viewModelScope.launch {
            repository.deleteObservation(observation)
        }
    }
    
    /**
     * Update een bestaande waarneming.
     */
    fun updateObservation(observation: ObservationEntity) {
        viewModelScope.launch {
            repository.updateObservation(observation)
        }
    }
}
