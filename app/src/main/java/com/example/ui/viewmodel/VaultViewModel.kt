package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autofill.AutofillHelper
import com.example.data.AppDatabase
import com.example.data.SecurityPreferences
import com.example.data.VaultAuditReport
import com.example.data.VaultItem
import com.example.data.VaultRepository
import com.example.security.BiometricAuthManager
import com.example.security.CryptoManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val securityPrefs = SecurityPreferences(application)
    val repository = VaultRepository(db.vaultDao(), securityPrefs)

    // Security & Lock State
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isPinSetupRequired = MutableStateFlow(!securityPrefs.isPinConfigured)
    val isPinSetupRequired: StateFlow<Boolean> = _isPinSetupRequired.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _biometricStatus = MutableStateFlow(BiometricAuthManager.checkBiometricAvailability(application))
    val biometricStatus: StateFlow<BiometricAuthManager.BiometricStatus> = _biometricStatus.asStateFlow()

    // Navigation & Category Filtering
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Password Generator
    private val _generatorLength = MutableStateFlow(16)
    val generatorLength: StateFlow<Int> = _generatorLength.asStateFlow()

    private val _generatorIncludeUpper = MutableStateFlow(true)
    val generatorIncludeUpper: StateFlow<Boolean> = _generatorIncludeUpper.asStateFlow()

    private val _generatorIncludeLower = MutableStateFlow(true)
    val generatorIncludeLower: StateFlow<Boolean> = _generatorIncludeLower.asStateFlow()

    private val _generatorIncludeNumbers = MutableStateFlow(true)
    val generatorIncludeNumbers: StateFlow<Boolean> = _generatorIncludeNumbers.asStateFlow()

    private val _generatorIncludeSymbols = MutableStateFlow(true)
    val generatorIncludeSymbols: StateFlow<Boolean> = _generatorIncludeSymbols.asStateFlow()

    private val _generatedPassword = MutableStateFlow(CryptoManager.generatePassword(16))
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    // Security Audit
    private val _auditReport = MutableStateFlow(VaultAuditReport(0, 0, 0, 100, emptyList(), emptyMap()))
    val auditReport: StateFlow<VaultAuditReport> = _auditReport.asStateFlow()

    // Autofill Service Status
    private val _isAutofillEnabled = MutableStateFlow(AutofillHelper.isAutofillServiceEnabled(application))
    val isAutofillEnabled: StateFlow<Boolean> = _isAutofillEnabled.asStateFlow()

    // Active Items Stream
    val vaultItems: StateFlow<List<VaultItem>> = combine(
        repository.allItems,
        _selectedCategory,
        _searchQuery
    ) { items, category, query ->
        var list = items
        if (category != "All") {
            list = if (category == "Favorites") {
                list.filter { it.isFavorite }
            } else {
                list.filter { it.category.equals(category, ignoreCase = true) }
            }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.urlOrPackage.lowercase().contains(q)
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var clipboardClearJob: Job? = null

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfEmpty()
            refreshAuditReport()
        }
    }

    fun refreshAutofillStatus() {
        _isAutofillEnabled.value = AutofillHelper.isAutofillServiceEnabled(getApplication())
    }

    fun refreshAuditReport() {
        viewModelScope.launch {
            _auditReport.value = repository.runSecurityAudit()
        }
    }

    // --- Master PIN & Unlock Logic ---

    fun setupMasterPin(pin: String): Boolean {
        if (pin.length < 4) {
            _pinError.value = "PIN must be at least 4 digits"
            return false
        }
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPin(pin, salt)
        securityPrefs.saveMasterPin(salt, hash)
        _isPinSetupRequired.value = false
        _isLocked.value = false
        _pinError.value = null
        securityPrefs.lastUnlockedTime = System.currentTimeMillis()
        return true
    }

    fun unlockWithPin(pin: String): Boolean {
        val salt = securityPrefs.pinSalt
        val expectedHash = securityPrefs.pinHash
        if (salt == null || expectedHash == null) {
            _isPinSetupRequired.value = true
            return false
        }
        val isValid = CryptoManager.verifyPin(pin, salt, expectedHash)
        if (isValid) {
            _isLocked.value = false
            _pinError.value = null
            securityPrefs.lastUnlockedTime = System.currentTimeMillis()
            refreshAuditReport()
            return true
        } else {
            _pinError.value = "Incorrect PIN. Try again."
            return false
        }
    }

    fun unlockWithBiometricSuccess() {
        _isLocked.value = false
        _pinError.value = null
        securityPrefs.lastUnlockedTime = System.currentTimeMillis()
        refreshAuditReport()
    }

    fun lockVault() {
        _isLocked.value = true
        _pinError.value = null
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Password Generator Actions ---

    fun setGeneratorLength(length: Int) {
        _generatorLength.value = length
        regeneratePassword()
    }

    fun toggleGeneratorUpper(enabled: Boolean) {
        _generatorIncludeUpper.value = enabled
        regeneratePassword()
    }

    fun toggleGeneratorLower(enabled: Boolean) {
        _generatorIncludeLower.value = enabled
        regeneratePassword()
    }

    fun toggleGeneratorNumbers(enabled: Boolean) {
        _generatorIncludeNumbers.value = enabled
        regeneratePassword()
    }

    fun toggleGeneratorSymbols(enabled: Boolean) {
        _generatorIncludeSymbols.value = enabled
        regeneratePassword()
    }

    fun regeneratePassword() {
        _generatedPassword.value = CryptoManager.generatePassword(
            length = _generatorLength.value,
            includeUpper = _generatorIncludeUpper.value,
            includeLower = _generatorIncludeLower.value,
            includeNumbers = _generatorIncludeNumbers.value,
            includeSymbols = _generatorIncludeSymbols.value
        )
    }

    fun generatePassphrase(): String {
        val phrase = CryptoManager.generatePassphrase(wordCount = 4)
        _generatedPassword.value = phrase
        return phrase
    }

    // --- Item CRUD Actions ---

    fun saveCredential(
        id: Long,
        title: String,
        username: String,
        password: String,
        urlOrPackage: String,
        category: String,
        notes: String,
        isFavorite: Boolean,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val item = VaultItem(
                id = id,
                title = title,
                username = username,
                plainPassword = password,
                urlOrPackage = urlOrPackage,
                category = category,
                notes = notes,
                isFavorite = isFavorite
            )
            repository.saveItem(item)
            refreshAuditReport()
            onComplete()
        }
    }

    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, isFavorite)
        }
    }

    fun deleteCredential(id: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteItem(id)
            refreshAuditReport()
            onComplete()
        }
    }

    // --- Secure Clipboard with Auto-Clear Timer ---

    fun copyToClipboard(label: String, text: String, isSensitive: Boolean = true) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)

        // Mark as sensitive on Android 13+ to avoid sensitive clipboard preview
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }

        clipboard.setPrimaryClip(clip)

        val clearDuration = securityPrefs.clipboardClearSeconds
        if (isSensitive && clearDuration > 0) {
            clipboardClearJob?.cancel()
            clipboardClearJob = viewModelScope.launch {
                Toast.makeText(
                    context,
                    "$label copied! Auto-clearing in ${clearDuration}s",
                    Toast.LENGTH_SHORT
                ).show()
                delay(clearDuration * 1000L)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                    Toast.makeText(context, "Clipboard cleared for security", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    // Ignore clipboard clear error if app not focused
                }
            }
        } else {
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }
}
