@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.yvesds.vt5.features.opstart.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.yvesds.vt5.R
import com.yvesds.vt5.core.opslag.SaFStorageHelper
import com.yvesds.vt5.core.secure.CredentialsStore
import com.yvesds.vt5.core.ui.DialogStyler
import com.yvesds.vt5.databinding.SchermInstallatieBinding
import com.yvesds.vt5.features.opstart.helpers.AliasIndexManager
import com.yvesds.vt5.features.opstart.helpers.InstallationDialogManager
import com.yvesds.vt5.features.opstart.helpers.InstallationSafManager
import com.yvesds.vt5.features.opstart.helpers.ServerAuthenticationManager
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.hoofd.HoofdActiviteit
import com.yvesds.vt5.hoofd.InstellingenScherm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * InstallatieScherm - Beheert de installatie en permissies.
 * Credentials en serverdata download zijn verplaatst naar het InstellingenScherm.
 */
class InstallatieScherm : AppCompatActivity() {
    companion object {
        private const val TAG = "InstallatieScherm"
    }

    private lateinit var binding: SchermInstallatieBinding
    private lateinit var saf: SaFStorageHelper
    private lateinit var safManager: InstallationSafManager
    private lateinit var authManager: ServerAuthenticationManager
    private lateinit var aliasManager: AliasIndexManager
    private lateinit var dialogManager: InstallationDialogManager
    
    private var dataPreloaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SchermInstallatieBinding.inflate(layoutInflater)
        setContentView(binding.root)

        saf = SaFStorageHelper(this)
        
        initializeHelpers()
        initUi()
        setupPermissionAcknowledgements()
        wireClicks()

        if (safManager.isSafConfigured()) {
            preloadDataIfExists()
        }
        updatePrecomputeButtonState()
    }

    private fun initializeHelpers() {
        safManager = InstallationSafManager(this, saf) { success ->
            binding.tvStatus.text = if (success) {
                preloadDataIfExists()
                getString(R.string.status_saf_ok)
            } else {
                getString(R.string.status_saf_niet_ingesteld)
            }
            updatePrecomputeButtonState()
        }
        authManager = ServerAuthenticationManager(this)
        aliasManager = AliasIndexManager(this, saf)
        dialogManager = InstallationDialogManager(this)
    }

    private fun initUi() {
        binding.etUitleg.setText(getString(R.string.install_uitleg))
        refreshSafStatus()
        binding.etUitleg.measure(0, 0)
    }

    private fun wireClicks() = with(binding) {
        btnKiesDocuments.setOnClickListener { 
            safManager.launchDocumentPicker()
        }

        btnCheckFolders.setOnClickListener {
            it.isEnabled = false
            try {
                val ok = safManager.ensureFoldersExist()
                tvStatus.text = if (ok) {
                    preloadDataIfExists()
                    getString(R.string.status_saf_ok)
                } else {
                    getString(R.string.status_saf_missing)
                }
                updatePrecomputeButtonState()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking folders: ${e.message}", e)
                dialogManager.showError("Fout bij controleren folders", e.message ?: "Onbekende fout")
            } finally {
                it.isEnabled = true
            }
        }

        btnAliasPrecompute.setOnClickListener {
            handleForceRebuildAliasIndex()
        }

        btnKlaar.setOnClickListener {
            navigateToOpstart()
        }

        btnOpenUnknownSources.setOnClickListener {
            openUnknownSourcesSettings()
        }
    }

    private fun setupPermissionAcknowledgements() {
        val prefs = getSharedPreferences("vt5_prefs", MODE_PRIVATE)
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasSaf = saf.getRootUri() != null
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val hasAlarm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else true

        bindPermCheckBox(binding.cbPermAudio, InstellingenScherm.PREF_PERM_AUDIO_ACK, prefs, hasAudio, R.string.perm_disable_message_audio)
        bindPermCheckBox(binding.cbPermSaf, InstellingenScherm.PREF_PERM_SAF_ACK, prefs, hasSaf, R.string.perm_disable_message_saf)
        bindPermCheckBox(binding.cbPermLocation, InstellingenScherm.PREF_PERM_LOCATION_ACK, prefs, hasLocation, R.string.perm_disable_message_location)
        bindPermCheckBox(binding.cbPermCamera, InstellingenScherm.PREF_PERM_CAMERA_ACK, prefs, hasCamera, 0)
        bindPermCheckBox(binding.cbPermBluetooth, InstellingenScherm.PREF_PERM_BLUETOOTH_ACK, prefs, hasBluetooth, 0)
        bindPermCheckBox(binding.cbPermAlarm, InstellingenScherm.PREF_PERM_ALARM_ACK, prefs, hasAlarm, 0)

        // Extra actie voor SAF
        binding.cbPermSaf.setOnClickListener {
            if (binding.cbPermSaf.isChecked) safManager.launchDocumentPicker()
        }
    }

    private fun bindPermCheckBox(cb: MaterialCheckBox, key: String, prefs: android.content.SharedPreferences, actualGranted: Boolean, disableMessageRes: Int) {
        var suppress = false
        val stored = prefs.getBoolean(key, false)
        val effective = stored || actualGranted
        if (effective && !stored) prefs.edit { putBoolean(key, true) }
        
        suppress = true
        cb.isChecked = effective
        suppress = false

        cb.setOnCheckedChangeListener { _, isChecked ->
            if (suppress) return@setOnCheckedChangeListener
            if (isChecked) {
                when (key) {
                    InstellingenScherm.PREF_PERM_AUDIO_ACK -> requestPermission(Manifest.permission.RECORD_AUDIO)
                    InstellingenScherm.PREF_PERM_LOCATION_ACK -> requestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    InstellingenScherm.PREF_PERM_CAMERA_ACK -> requestPermission(Manifest.permission.CAMERA)
                    InstellingenScherm.PREF_PERM_BLUETOOTH_ACK -> requestPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    InstellingenScherm.PREF_PERM_ALARM_ACK -> {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = "package:$packageName".toUri() })
                        }
                    }
                }
                prefs.edit { putBoolean(key, true) }
            } else {
                showDisablePermissionDialog(disableMessageRes) { confirmed ->
                    if (confirmed) prefs.edit { putBoolean(key, false) }
                    else { suppress = true; cb.isChecked = true; suppress = false }
                }
            }
        }
    }

    private fun requestPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 100)
        }
    }

    private fun showDisablePermissionDialog(messageRes: Int, onResult: (Boolean) -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.perm_disable_title)
            .setMessage(messageRes)
            .setPositiveButton(R.string.perm_disable_confirm) { _, _ -> onResult(true) }
            .setNegativeButton(R.string.perm_disable_cancel) { _, _ -> onResult(false) }
            .setCancelable(false)
            .show()
        DialogStyler.apply(dialog)
    }

    private fun openUnknownSourcesSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Kan installatie-instellingen niet openen.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleForceRebuildAliasIndex() {
        val vt5Dir = safManager.getVt5Directory() ?: return
        binding.btnAliasPrecompute.isEnabled = false
        binding.btnAliasPrecompute.alpha = 0.5f
        lifecycleScope.launch {
            val progressDialog = dialogManager.showProgress("Forceer heropbouw alias index...")
            try {
                val result = aliasManager.forceRebuildIndex(vt5Dir, authManager.generateIsoTimestamp())
                progressDialog.dismiss()
                if (result is AliasIndexManager.RegenerationResult.Success) dialogManager.showInfo("Succes", "Alias index is succesvol opnieuw opgebouwd")
                else if (result is AliasIndexManager.RegenerationResult.Failure) dialogManager.showError("Fout bij forceer rebuild", result.error)
            } catch (e: Exception) {
                dialogManager.showError("Fout bij forceer rebuild", e.message ?: "Onbekende fout")
                progressDialog.dismiss()
            } finally { updatePrecomputeButtonState() }
        }
    }

    private fun preloadDataIfExists() {
        if (dataPreloaded) return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ServerDataCache.preload(applicationContext) }
                dataPreloaded = true
            } catch (_: Exception) {
            } finally { updatePrecomputeButtonState() }
        }
    }

    private fun updatePrecomputeButtonState() {
        val hasFolder = safManager.isSafConfigured()
        binding.btnAliasPrecompute.isEnabled = hasFolder
        binding.btnAliasPrecompute.alpha = if (hasFolder) 1.0f else 0.5f
    }

    private fun refreshSafStatus() {
        val ok = safManager.isSafConfigured()
        binding.tvStatus.text = if (ok) getString(R.string.status_saf_ok) else getString(R.string.status_saf_niet_ingesteld)
    }

    private fun navigateToOpstart() {
        startActivity(Intent(this, HoofdActiviteit::class.java))
        finish()
    }
}
