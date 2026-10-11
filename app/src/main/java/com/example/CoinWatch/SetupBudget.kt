package com.example.CoinWatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.BudgetGoal
import com.example.CoinWatch.data.BudgetGoalDao
import com.example.CoinWatch.data.Income
import com.example.CoinWatch.data.SyncRepository
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupBudget : AppCompatActivity() {
    private lateinit var budgetDao: BudgetGoalDao
    private lateinit var db: AppDatabase
    private lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_budget)

        db = AppDatabase.getDatabase(applicationContext)
        budgetDao = db.budgetGoalDao()
        syncRepository = SyncRepository(applicationContext)

        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userId = sharedPref.getInt("USER_ID", -1)

        // UI Components
        val btnBack = findViewById<ImageButton>(R.id.imageButton25)
        val btnSave = findViewById<Button>(R.id.btnSaveBudget)
        val spinner = findViewById<Spinner>(R.id.spinner_month)
        val incomeEditText = findViewById<EditText>(R.id.editTextIncome)
        val minGoalEditText = findViewById<EditText>(R.id.editTextMinGoal)
        val maxGoalEditText = findViewById<EditText>(R.id.editTextMaxGoal)

        if (userId != -1) {
            val savedIncome = sharedPref.getFloat("USER_INCOME_$userId", 0.0f)
            if (savedIncome > 0f) {
                incomeEditText.setText(savedIncome.toString())
            }
        }

        // Back Navigation
        btnBack.setOnClickListener {
            startActivity(Intent(this, HomeScreen::class.java))
            finish()
        }

        btnSave.setOnClickListener {
            if (userId == -1) {
                Log.e("SetupBudget", "User ID not found")
                showToast("User ID not found. Please log in again.")
                return@setOnClickListener
            }

            val incomeStr = incomeEditText.text?.toString()?.trim()
            val selectedMonth = spinner.selectedItem?.toString()?.trim()
            val minStr = minGoalEditText.text?.toString()?.trim()
            val maxStr = maxGoalEditText.text?.toString()?.trim()

            if (incomeStr.isNullOrEmpty() || selectedMonth.isNullOrEmpty() || minStr.isNullOrEmpty() || maxStr.isNullOrEmpty()) {
                showToast("Please fill all fields (Income, Month, Min & Max budget goals).")
                return@setOnClickListener
            }

            val income = incomeStr.toDoubleOrNull()
            val minGoal = minStr.toDoubleOrNull()
            val maxGoal = maxStr.toDoubleOrNull()

            if (income == null || minGoal == null || maxGoal == null) {
                showToast("Income and budget goals must be valid numbers.")
                return@setOnClickListener
            }

            if (minGoal > maxGoal) {
                showToast("Min goal can't be greater than Max goal.")
                return@setOnClickListener
            }

            sharedPref.edit().putFloat("USER_INCOME_$userId", income.toFloat()).apply()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val existingGoal = budgetDao.getBudgetForUserAndMonth(userId, selectedMonth)
                    if (existingGoal != null) {
                        withContext(Dispatchers.Main) {
                            showToast("Budget for this month already exists.")
                        }
                        return@launch
                    }

                    val goal = BudgetGoal(
                        user_id = userId,
                        month = selectedMonth,
                        minGoal = minGoal,
                        maxGoal = maxGoal
                    )

                    val currentDate = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
                    val incomeRecord = Income(
                        user_id = userId,
                        title = "Monthly Income ($selectedMonth)",
                        amount = income,
                        date = currentDate
                    )

                    syncRepository.insertIncome(incomeRecord)
                    syncRepository.insertBudgetGoal(goal)
                    syncRepository.syncPendingData()

                    withContext(Dispatchers.Main) {
                        showToast("Income & Budget saved successfully!")
                        startActivity(Intent(this@SetupBudget, HomeScreen::class.java))
                        finish()
                    }

                } catch (e: Exception) {
                    Log.e("SetupBudget", "Insert failed: ${e.localizedMessage}", e)
                    withContext(Dispatchers.Main) {
                        showToast("Error saving budget. Please try again.")
                    }
                }
            }
        }

        val btnReset = findViewById<Button>(R.id.button11)
        btnReset.setOnClickListener {
            incomeEditText.text.clear()
            minGoalEditText.text.clear()
            maxGoalEditText.text.clear()
            spinner.setSelection(0)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this@SetupBudget, message, Toast.LENGTH_SHORT).show()
    }
}
