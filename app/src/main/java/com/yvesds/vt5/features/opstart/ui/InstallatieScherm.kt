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
import com.yvesds.vt5.features.opstart.helpers.ServerDataDownloadManager
import com.yvesds.vt5.features.serverdata.model.ServerDataCache
import com.yvesds.vt5.hoofd.HoofdActiviteit
import com.yvesds.vt5.hoofd.InstellingenScherm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * InstallatieScherm - Beheert de installatie en permissies.
 * Nu met actieve permissie-aanvragen via de checkboxen.
 */
class InstallatieScherm : AppCompatActivity() {
    companion object {
        private const val TAG = "InstallatieScherm"
    }

    private lateinit var binding: SchermInstallatieBinding
    private lateinit var saf: SaFStorageHelper
    private lateinit var creds: CredentialsStore
    private lateinit var safManager: InstallationSafManager
    private lateinit var authManager: ServerAuthenticationManager
    private lateinit var downloadManager: ServerDataDownloadManager
    private lateinit var aliasManager: AliasIndexManager
    private lateinit var dialogManager: InstallationDialogManager
    
    private var dataPreloaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SchermInstallatieBinding.inflate(layoutInflater)
        setContentView(binding.root)

        saf = SaFStorageHelper(this)
        creds = CredentialsStore(this)
        
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
        downloadManager = ServerDataDownloadManager(this)
        aliasManager = AliasIndexManager(this, saf)
        dialogManager = InstallationDialogManager(this)
    }

    private fun initUi() {
        binding.etUitleg.setText(getString(R.string.install_uitleg))
        restoreCreds()
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

        btnWis.setOnClickListener {
            it.isEnabled = false
            try {
                creds.clear()
                etLogin.setText("")
                etPass.setText("")
                Toast.makeText(this@InstallatieScherm, getString(R.string.msg_credentials_gewist), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing credentials: ${e.message}", e)
                dialogManager.showError("Fout bij wissen credentials", e.message ?: "Onbekende fout")
            } finally {
                it.isEnabled = true
            }
        }

        btnBewaar.setOnClickListener {
            it.isEnabled = false
            try {
                val username = etLogin.text?.toString().orEmpty().trim()
                val password = etPass.text?.toString().orEmpty()
                creds.save(username, password)
                Toast.makeText(this@InstallatieScherm, getString(R.string.msg_credentials_opgeslagen), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving credentials: ${e.message}", e)
                dialogManager.showError("Fout bij opslaan credentials", e.message ?: "Onbekende fout")
            } finally {
                it.isEnabled = true
            }
        }

        btnLoginTest.setOnClickListener {
            val (username, password) = getCredentialsOrWarn() ?: return@setOnClickListener
            it.isEnabled = false
            handleLoginTest(username, password)
        }

        btnDownloadJsons.setOnClickListener {
            val (username, password) = getCredentialsOrWarn() ?: return@setOnClickListener
            it.isEnabled = false
            handleDownloadServerData(username, password)
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

    private fun getCredentialsOrWarn(): Pair<String, String>? {
        val username = binding.etLogin.text?.toString().orEmpty().trim()
        val password = binding.etPass.text?.toString().orEmpty()
        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, getString(R.string.msg_vul_login_eerst), Toast.LENGTH_LONG).show()
            return null
        }
        return username to password
    }

    private fun handleLoginTest(username: String, password: String) {
        lifecycleScope.launch {
            val progressDialog = dialogManager.showProgress("Login testen...")
            try {
                val result = authManager.testLogin(username, password)
                progressDialog.dismiss()
                if (result is ServerAuthenticationManager.AuthResult.Success) {
                    dialogManager.showInfo(getString(R.string.dlg_titel_result), result.response)
                    creds.save(username, password)
                    authManager.saveFullnameToPreferences(result.response)
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val serverdataDir = safManager.getSubdirectory("serverdata", createIfMissing = true)
                            authManager.saveCheckUserResponse(serverdataDir, result.response)
                        } catch (_: Exception) {}
                    }
                } else if (result is ServerAuthenticationManager.AuthResult.Failure) {
                    dialogManager.showError("Login mislukt", result.error)
                }
            } catch (e: Exception) {
                dialogManager.showError("Fout bij login test", e.message ?: "Onbekende fout")
                progressDialog.dismiss()
            } finally {
                binding.btnLoginTest.isEnabled = true
            }
        }
    }

    private fun handleDownloadServerData(username: String, password: String) {
        val vt5Dir = safManager.getVt5Directory()
        if (vt5Dir == null) {
            Toast.makeText(this, getString(R.string.msg_kies_documents_eerst), Toast.LENGTH_LONG).show()
            binding.btnDownloadJsons.isEnabled = true
            return
        }
        val serverdataDir = safManager.getSubdirectory("serverdata", createIfMissing = true)
        val binariesDir = safManager.getSubdirectory("binaries", createIfMissing = true)

        lifecycleScope.launch {
            val progressDialog = dialogManager.showProgress("JSONs downloaden...")
            try {
                val downloadResult = downloadManager.downloadAllServerData(serverdataDir, binariesDir, username, password) { message ->
                    dialogManager.updateProgress(progressDialog, message)
                }
                if (downloadResult is ServerDataDownloadManager.DownloadResult.Success) {
                    if (aliasManager.needsRegeneration(vt5Dir)) {
                        aliasManager.regenerateIndexIfNeeded(vt5Dir, authManager.generateIsoTimestamp()) { message ->
                            dialogManager.updateProgress(progressDialog, message)
                        }
                    }
                    dataPreloaded = false
                    progressDialog.dismiss()
                    dialogManager.showInfo(getString(R.string.dlg_titel_result), downloadResult.messages.joinToString("\n"))
                    preloadDataIfExists()
                } else if (downloadResult is ServerDataDownloadManager.DownloadResult.Failure) {
                    progressDialog.dismiss()
                    dialogManager.showError("Fout bij downloaden", downloadResult.error)
                }
            } catch (e: Exception) {
                dialogManager.showError("Fout bij downloaden", e.message ?: "Onbekende fout")
                progressDialog.dismiss()
            } finally {
                binding.btnDownloadJsons.isEnabled = true
                updatePrecomputeButtonState()
            }
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

    private fun navigateToOpstart() {
        try {
            val intent = Intent(this, HoofdActiviteit::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        } catch (_: Exception) { finish() }
    }

    private fun restoreCreds() {
        binding.etLogin.setText(creds.getUsername().orEmpty())
        binding.etPass.setText(creds.getPassword().orEmpty())
    }

    private fun refreshSafStatus() {
        val uri = saf.getRootUri()
        val ok = uri != null && saf.foldersExist()
        binding.tvStatus.text = when {
            uri == null -> getString(R.string.status_saf_niet_ingesteld)
            ok -> getString(R.string.status_saf_ok)
            else -> getString(R.string.status_saf_missing)
        }
    }

    private fun updatePrecomputeButtonState() = with(binding) {
        val vt5Dir = safManager.getVt5Directory()
        val present = vt5Dir != null && aliasManager.isIndexPresent(vt5Dir)
        btnAliasPrecompute.isEnabled = !present
        btnAliasPrecompute.alpha = if (present) 0.5f else 1.0f
    }
}
