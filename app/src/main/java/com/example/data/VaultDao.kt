package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_items ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY isFavorite DESC, updatedAt DESC")
    suspend fun getAllItemsDirect(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE category = :category ORDER BY isFavorite DESC, updatedAt DESC")
    fun getItemsByCategory(category: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavorites(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%' OR urlOrPackage LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchItems(query: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    fun getItemById(id: Long): Flow<VaultItemEntity?>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getItemByIdDirect(id: Long): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE urlOrPackage LIKE '%' || :domainOrPackage || '%' OR title LIKE '%' || :domainOrPackage || '%'")
    suspend fun findByPackageOrDomain(domainOrPackage: String): List<VaultItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity): Long

    @Update
    suspend fun update(item: VaultItemEntity)

    @Delete
    suspend fun delete(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM vault_items")
    fun getCount(): Flow<Int>
}
