package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["urlOrPackage"]),
        Index(value = ["category"]),
        Index(value = ["title"])
    ]
)
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val username: String,
    val encryptedPassword: String,
    val urlOrPackage: String = "",
    val category: String = "Logins",
    val notesEncrypted: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
