package com.yvesds.vt5.features.telling

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yvesds.vt5.VT5App
import com.yvesds.vt5.core.database.VoiceTallyDatabase
import com.yvesds.vt5.core.database.repository.HybridTellingRepository
import com.yvesds.vt5.core.database.toServerEnvelope
import com.yvesds.vt5.core.database.toServerItem
import com.yvesds.vt5.core.opslag.FileLogger
import com.yvesds.vt5.features.telling.TellingScherm.SoortRow
import com.yvesds.vt5.hoofd.InstellingenScherm
import com.yvesds.vt5.net.ServerTellingDataItem
import com.yvesds.vt5.net.ServerTellingEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * ViewModel that coordinates with RecordsBeheer for collecting finals and exposes LiveData for UI.
 */
class TellingViewModel : ViewModel() {

    private val _tiles = MutableLiveData<List<SoortRow>>(emptyList())
    val tiles: LiveData<List<SoortRow>> = _tiles

    private val _partials = MutableLiveData<List<TellingScherm.SpeechLogRow>>(emptyList())
    val partials: LiveData<List<TellingScherm.SpeechLogRow>> = _partials

    private val _finals = MutableLiveData<List<TellingScherm.SpeechLogRow>>(emptyList())
    val finals: LiveData<List<TellingScherm.SpeechLogRow>> = _finals

    private val _pendingRecords = MutableLiveData<List<ServerTellingDataItem>>(emptyList())
    val pendingRecords: LiveData<List<ServerTellingDataItem>> = _pendingRecords

    private val _activeEnvelope = MutableLiveData<ServerTellingEnvelope?>(null)
    val activeEnvelope: LiveData<ServerTellingEnvelope?> = _activeEnvelope

    // Expose repository error messages as single LiveData string (UI can display Toast)
    private val _repoError = MutableLiveData<String?>(null)
    val repoError: LiveData<String?> = _repoError

    // RecordsBeheer injected at runtime
    private lateinit var recordsBeheer: RecordsBeheer
    private var recordsCollectorJob: Job? = null
    
    private val hybridRepository by lazy { HybridTellingRepository(recordsBeheer.getContext()) }
    private val fileLogger by lazy { FileLogger(recordsBeheer.getContext()) }

    fun setRecordsBeheer(rb: RecordsBeheer) {
        this.recordsBeheer = rb
        // Initial snapshot
        _pendingRecords.value = rb.getPendingRecordsSnapshot()
        
        viewModelScope.launch {
            fileLogger.info("TellingViewModel: RecordsBeheer gekoppeld")
        }
    }

    /**
     * Start observing records based on the selected storage mode.
     */
    fun observeRecords(tellingId: String) {
        if (!::recordsBeheer.isInitialized) {
            android.util.Log.e("TellingViewModel", "observeRecords: recordsBeheer NOT INITIALIZED")
            return
        }
        
        recordsCollectorJob?.cancel()
        recordsCollectorJob = viewModelScope.launch {
            try {
                val mode = InstellingenScherm.getStorageMode(recordsBeheer.getContext())
                fileLogger.info("TellingViewModel: Start observatie (Modus: $mode, Telling: $tellingId)")
                
                if (mode == InstellingenScherm.STORAGE_MODE_ROOM) {
                    hybridRepository.getWaarnemingenFlow(tellingId).collect { roomRecords ->
                        _pendingRecords.postValue(roomRecords.map { it.toServerItem() })
                    }
                } else {
                    recordsBeheer.pendingRecordsFlow.collect { list ->
                        _pendingRecords.postValue(list)
                    }
                }
            } catch (e: Exception) {
                fileLogger.error("TellingViewModel: Fout bij observeren records: ${e.message}")
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
     * Collect a final via the repository. The ViewModel orchestrates the call and updates LiveData.
     * UI should call this (keeps Activity thin).
     */
    fun collectFinal(soortId: String, amount: Int, explicitTijdstipSeconds: Long? = null) {
        if (!::recordsBeheer.isInitialized) {
            _repoError.value = "Repository niet geïnitialiseerd"
            return
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                recordsBeheer.collectFinalAsRecord(soortId, amount, explicitTijdstipSeconds)
            }
            when (result) {
                is OperationResult.Success -> {
                    // pendingRecordsFlow will update LiveData via collector; clear any previous error
                    _repoError.value = null
                }
                is OperationResult.Failure -> {
                    _repoError.value = result.reason
                }
            }
        }
    }

    // allow Activity to directly append a pending record (compat)
    fun addPendingRecord(item: ServerTellingDataItem) {
        _pendingRecords.value = (_pendingRecords.value ?: emptyList()) + item
    }

    fun setPendingRecords(list: List<ServerTellingDataItem>) { 
        _pendingRecords.value = list 
        updateActiveEnvelope(list)
    }
    fun clearPendingRecords() { 
        _pendingRecords.value = emptyList() 
        _activeEnvelope.value = null
    }

    /**
     * Set the base metadata envelope. This should be called once when starting or restoring a telling.
     */
    fun setBaseEnvelope(envelope: ServerTellingEnvelope) {
        _activeEnvelope.value = envelope.copy(data = _pendingRecords.value ?: emptyList())
    }

    /**
     * Load base envelope from Room or SharedPreferences.
     */
    fun loadBaseEnvelope(tellingId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = VoiceTallyDatabase.getDatabase(recordsBeheer.getContext()).tellingDao()
                val header = dao.getHeader(tellingId)
                if (header != null) {
                    val roomRecords = dao.getWaarnemingenList(tellingId)
                    val envelope = header.toServerEnvelope(roomRecords)
                    _activeEnvelope.postValue(envelope)
                    fileLogger.info("TellingViewModel: Base envelope geladen uit Room voor $tellingId")
                } else {
                    // Fallback to SharedPreferences
                    val prefs = recordsBeheer.getContext().getSharedPreferences("vt5_prefs", 0)
                    val json = prefs.getString("pref_saved_envelope_json", null)
                    if (json != null) {
                        val list = VT5App.json.decodeFromString(ListSerializer(ServerTellingEnvelope.serializer()), json)
                        if (list.isNotEmpty()) {
                            _activeEnvelope.postValue(list[0])
                            fileLogger.info("TellingViewModel: Base envelope geladen uit JSON voor $tellingId")
                        }
                    }
                }
            } catch (e: Exception) {
                fileLogger.error("TellingViewModel: Fout bij laden base envelope: ${e.message}")
            }
        }
    }

    /**
     * Update the active envelope with the latest records and calculate counts.
     */
    private fun updateActiveEnvelope(records: List<ServerTellingDataItem>) {
        val current = _activeEnvelope.value ?: return
        val nrec = records.size
        val nsoort = records.map { it.soortid }.filter { it.isNotBlank() }.toSet().size
        
        _activeEnvelope.value = current.copy(
            data = records,
            nrec = nrec.toString(),
            nsoort = nsoort.toString()
        )
    }
}
