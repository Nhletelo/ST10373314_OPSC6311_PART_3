package com.example.CoinWatch.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

//Entity
@Entity(
    tableName = "budget_goals",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["user_id"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("user_id")]
)
data class BudgetGoal(
    @PrimaryKey(autoGenerate = true) val goal_id: Int = 0,
    @ColumnInfo(name = "user_id") val user_id: Int,
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "min_goal") val minGoal: Double,
    @ColumnInfo(name = "max_goal") val maxGoal: Double,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false
)
