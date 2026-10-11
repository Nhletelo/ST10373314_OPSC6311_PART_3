package com.example.CoinWatch.SupabaseClient

import android.util.Log
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Supabase {
    val client = createSupabaseClient(
        supabaseUrl = "https://hhlnmrzkzmcwxyawpasj.supabase.co",
        supabaseKey = "sb_publishable_SbZ9pIZvFEg6xt6t_2xHvQ_ZSnEiDI9"
    ) {
        install(Postgrest)
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            client.from("todos").select {
                limit(1)
            }
            Log.d("SupabaseTest", "Supabase connected successfully!")
            true
        } catch (e: Exception) {
            Log.e("SupabaseTest", "Supabase connection test failed: ${e.message}", e)
            false
        }
    }
}
