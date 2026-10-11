package com.example.CoinWatch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

//Entity
@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["user_id"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("user_id")]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val category_id: Int = 0,
    @ColumnInfo(name = "category_name") val category_name: String,
    @ColumnInfo(name = "user_id") val user_id: Int,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
)
