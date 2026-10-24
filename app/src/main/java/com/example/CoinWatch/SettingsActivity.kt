package com.example.CoinWatch

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.CoinWatch.SupabaseClient.Supabase
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.utils.NetworkUtils
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        // Load settings into the FrameLayout
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings_container, SettingsFragment())
            }
        }

        // Back button functionality
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            findPreference<Preference>("delete_budget")?.setOnPreferenceClickListener {
                val context = requireContext()
                AlertDialog.Builder(context)
                    .setTitle("Delete Budget Goals")
                    .setMessage("Are you sure you want to delete all your monthly budget goals?")
                    .setPositiveButton("Delete") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            val prefs = context.getSharedPreferences("MyAppPrefs", AppCompatActivity.MODE_PRIVATE)
                            val userId = prefs.getInt("USER_ID", -1)
                            if (userId != -1) {
                                val db = AppDatabase.getDatabase(context)
                                val goals = db.budgetGoalDao().getGoalsByUser(userId)
                                for (g in goals) {
                                    db.budgetGoalDao().deleteGoal(g)
                                    if (NetworkUtils.isOnline(context)) {
                                        try {
                                            Supabase.client.from("budget_goals").delete {
                                                filter { eq("goal_id", g.goal_id) }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("Settings", "Failed to delete budget from Supabase", e)
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "All budget goals deleted successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
    }
}
