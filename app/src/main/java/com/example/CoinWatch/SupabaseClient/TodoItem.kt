package com.example.CoinWatch.SupabaseClient

import kotlinx.serialization.Serializable

@Serializable
data class TodoItem(
    val id: Int = 0,
    val name: String
)
