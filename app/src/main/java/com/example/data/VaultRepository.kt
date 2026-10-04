package com.example.data

import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class VaultItem(
    val id: Long = 0,
    val title: String,
    val username: String,
    val plainPassword: String,
    val urlOrPackage: String = "",
    val category: String = "Logins",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val passwordStrength: CryptoManager.PasswordAnalysis
        get() = CryptoManager.analyzePassword(plainPassword)
}

data class VaultAuditReport(
    val totalCount: Int,
    val weakCount: Int,
    val reusedCount: Int,
    val healthScore: Int, // 0 - 100
    val weakItems: List<VaultItem>,
    val reusedGroups: Map<String, List<VaultItem>>
)

class VaultRepository(
    private val vaultDao: VaultDao,
    val securityPreferences: SecurityPreferences
) {
    val allItems: Flow<List<VaultItem>> = vaultDao.getAllItems().map { list ->
        list.map { it.toModel() }
    }

    val favoriteItems: Flow<List<VaultItem>> = vaultDao.getFavorites().map { list ->
        list.map { it.toModel() }
    }

    fun getItemsByCategory(category: String): Flow<List<VaultItem>> {
        return if (category == "All") {
            allItems
        } else if (category == "Favorites") {
            favoriteItems
        } else {
            vaultDao.getItemsByCategory(category).map { list ->
                list.map { it.toModel() }
            }
        }
    }

    fun searchItems(query: String): Flow<List<VaultItem>> {
        return vaultDao.searchItems(query).map { list ->
            list.map { it.toModel() }
        }
    }

    fun getItemById(id: Long): Flow<VaultItem?> {
        return vaultDao.getItemById(id).map { it?.toModel() }
    }

    suspend fun getItemByIdDirect(id: Long): VaultItem? {
        return vaultDao.getItemByIdDirect(id)?.toModel()
    }

    suspend fun findByPackageOrDomain(domainOrPackage: String): List<VaultItem> {
        return vaultDao.findByPackageOrDomain(domainOrPackage).map { it.toModel() }
    }

    suspend fun getAllItemsDirect(): List<VaultItem> {
        return vaultDao.getAllItemsDirect().map { it.toModel() }
    }

    suspend fun saveItem(item: VaultItem): Long {
        val encryptedPassword = CryptoManager.encrypt(item.plainPassword)
        val encryptedNotes = CryptoManager.encrypt(item.notes)
        val entity = VaultItemEntity(
            id = item.id,
            title = item.title.trim(),
            username = item.username.trim(),
            encryptedPassword = encryptedPassword,
            urlOrPackage = item.urlOrPackage.trim(),
            category = item.category,
            notesEncrypted = encryptedNotes,
            isFavorite = item.isFavorite,
            createdAt = if (item.id == 0L) System.currentTimeMillis() else item.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        return if (item.id == 0L) {
            vaultDao.insert(entity)
        } else {
            vaultDao.update(entity)
            item.id
        }
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        val item = vaultDao.getItemByIdDirect(id) ?: return
        vaultDao.update(item.copy(isFavorite = isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteItem(id: Long) {
        vaultDao.deleteById(id)
    }

    suspend fun runSecurityAudit(): VaultAuditReport {
        val items = getAllItemsDirect()
        if (items.isEmpty()) {
            return VaultAuditReport(0, 0, 0, 100, emptyList(), emptyMap())
        }

        val weakItems = items.filter { it.passwordStrength.score < 50 }

        // Find reused passwords (grouping by decrypted plain password)
        val passwordGroup = items.groupBy { it.plainPassword.trim() }
        val reusedGroups = passwordGroup.filter { it.value.size > 1 && it.key.isNotEmpty() }
        val reusedCount = reusedGroups.values.sumOf { it.size }

        // Compute Vault Health Score
        val weakPenalty = (weakItems.size.toDouble() / items.size) * 50
        val reusedPenalty = (reusedCount.toDouble() / items.size) * 50
        val healthScore = (100 - (weakPenalty + reusedPenalty).toInt()).coerceIn(10, 100)

        return VaultAuditReport(
            totalCount = items.size,
            weakCount = weakItems.size,
            reusedCount = reusedCount,
            healthScore = healthScore,
            weakItems = weakItems,
            reusedGroups = reusedGroups
        )
    }

    suspend fun seedSampleDataIfEmpty() {
        if (!securityPreferences.isSampleSeeded) {
            val existing = vaultDao.getAllItemsDirect()
            if (existing.isEmpty()) {
                val samples = listOf(
                    VaultItem(
                        title = "Google Account",
                        username = "alex.morgan@gmail.com",
                        plainPassword = "G!8x" + CryptoManager.generatePassword(12),
                        urlOrPackage = "google.com",
                        category = "Logins",
                        notes = "Primary personal Google Account with 2FA enabled",
                        isFavorite = true
                    ),
                    VaultItem(
                        title = "GitHub",
                        username = "alex-dev-vault",
                        plainPassword = "Git#" + CryptoManager.generatePassword(14),
                        urlOrPackage = "github.com",
                        category = "Work",
                        notes = "SSH key configured on primary workstation",
                        isFavorite = true
                    ),
                    VaultItem(
                        title = "Netflix",
                        username = "alex.morgan@gmail.com",
                        plainPassword = "Flix" + CryptoManager.generatePassword(10),
                        urlOrPackage = "netflix.com",
                        category = "Entertainment",
                        notes = "4K UHD Family Subscription",
                        isFavorite = false
                    ),
                    VaultItem(
                        title = "Apex Finance Bank",
                        username = "amorgan_apex",
                        plainPassword = "Fin$" + CryptoManager.generatePassword(16),
                        urlOrPackage = "apexbank.com",
                        category = "Finance",
                        notes = "Checking & savings accounts. Security questions saved in offline backup.",
                        isFavorite = true
                    )
                )
                for (s in samples) {
                    saveItem(s)
                }
            }
            securityPreferences.isSampleSeeded = true
        }
    }

    private fun VaultItemEntity.toModel(): VaultItem {
        val plainPassword = CryptoManager.decrypt(encryptedPassword)
        val plainNotes = CryptoManager.decrypt(notesEncrypted)
        return VaultItem(
            id = id,
            title = title,
            username = username,
            plainPassword = plainPassword,
            urlOrPackage = urlOrPackage,
            category = category,
            notes = plainNotes,
            isFavorite = isFavorite,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
