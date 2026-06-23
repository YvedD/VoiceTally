package com.yvesds.vt5.features.telling

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.vt5.features.telling.TellingScherm.SoortRow
import com.yvesds.vt5.features.telling.data.TellingRepository
import com.yvesds.vt5.net.ServerTellingDataItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel voor het TellingScherm.
 * Coördineert met TellingRepository voor het opslaan en ophalen van waarnemingen.
 */
@HiltViewModel
class TellingViewModel @Inject constructor(
    val repository: TellingRepository
) : ViewModel() {

    private val _tiles = MutableLiveData<List<SoortRow>>(emptyList())
    val tiles: LiveData<List<SoortRow>> = _tiles

    private val _partials = MutableLiveData<List<TellingScherm.SpeechLogRow>>(emptyList())
    val partials: LiveData<List<TellingScherm.SpeechLogRow>> = _partials

    private val _finals = MutableLiveData<List<TellingScherm.SpeechLogRow>>(emptyList())
    val finals: LiveData<List<TellingScherm.SpeechLogRow>> = _finals

    private val _pendingRecords = MutableLiveData<List<ServerTellingDataItem>>(emptyList())
    val pendingRecords: LiveData<List<ServerTellingDataItem>> = _pendingRecords

    private val _repoError = MutableLiveData<String?>(null)
    val repoError: LiveData<String?> = _repoError

    private var currentTellingId: String? = null
    private var recordsCollectorJob: Job? = null

    /**
     * Zet de actieve telling ID en start het observeren van waarnemingen uit de database.
     */
    fun setTellingId(tellingId: String?) {
        this.currentTellingId = tellingId
        
        // Stop vorige observatie
        recordsCollectorJob?.cancel()
        
        if (tellingId == null) {
            _pendingRecords.postValue(emptyList())
            return
        }

        // Start nieuwe observatie van de database
        recordsCollectorJob = viewModelScope.launch {
            repository.getObservationsFlow(tellingId).collect { list ->
                _pendingRecords.postValue(list.map { with(repository) { it.toDomain() } })
            }
        }
    }

    fun setTiles(list: List<SoortRow>) { _tiles.value = list }
    fun updateTiles(list: List<SoortRow>) { _tiles.value = list }
    fun clearTiles() { _tiles.value = emptyList() }

    fun setPartials(list: List<TellingScherm.SpeechLogRow>) { _partials.value = list }
    fun appendPartial(row: TellingScherm.SpeechLogRow) { _partials.value = (_partials.value ?: emptyList()) + row }
    fun clearPartials() { _partials.value = emptyList() }

    fun setFinals(list: List<TellingScherm.SpeechLogRow>) { _finals.value = list }
    fun appendFinal(row: TellingScherm.SpeechLogRow) { _finals.value = (_finals.value ?: emptyList()) + row }
    fun clearFinals() { _finals.value = emptyList() }

    /**
     * Slaat een nieuwe waarneming op in de Room database.
     */
    fun addObservation(record: ServerTellingDataItem) {
        val tellingId = currentTellingId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.addObservation(tellingId, record)
                }
                _repoError.postValue(null)
            } catch (e: Exception) {
                _repoError.postValue(e.message ?: "Fout bij toevoegen waarneming")
            }
        }
    }

    /**
     * Werkt een bestaande waarneming bij in de Room database.
     */
    fun updateObservation(record: ServerTellingDataItem) {
        val tellingId = currentTellingId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Mapping terug naar entity via id
                    val existing = repository.getObservations(tellingId).find { it.id == record.idLocal }
                    if (existing != null) {
                        // Hier zouden we een mapping van record -> existing entity moeten doen
                        // Voor nu gebruiken we de repository addObservation die REPLACE doet indien ID hetzelfde is
                        repository.addObservation(tellingId, record)
                    }
                }
                _repoError.postValue(null)
            } catch (e: Exception) {
                _repoError.postValue(e.message ?: "Fout bij bijwerken waarneming")
            }
        }
    }

    /**
     * Collect a final via the repository.
     */
    fun collectFinal(soortId: String, amount: Int, explicitTijdstipSeconds: Long? = null) {
        val tellingId = currentTellingId ?: return
        
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val timestamp = explicitTijdstipSeconds ?: (System.currentTimeMillis() / 1000L)
                    val record = ServerTellingDataItem(
                        tellingid = tellingId,
                        soortid = soortId,
                        aantal = amount.toString(),
                        tijdstip = timestamp.toString()
                    )
                    repository.addObservation(tellingId, record)
                }
                _repoError.postValue(null)
            } catch (e: Exception) {
                _repoError.postValue(e.message ?: "Fout bij opslaan waarneming")
            }
        }
    }

    fun addPendingRecord(item: ServerTellingDataItem) {
        _pendingRecords.value = (_pendingRecords.value ?: emptyList()) + item
    }

    fun setPendingRecords(list: List<ServerTellingDataItem>) { _pendingRecords.value = list }
    fun clearPendingRecords() { _pendingRecords.value = emptyList() }
}
