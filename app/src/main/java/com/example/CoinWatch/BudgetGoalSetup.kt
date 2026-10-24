package com.example.CoinWatch

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.BudgetGoal
import com.example.CoinWatch.data.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgetGoalSetup : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var syncRepository: SyncRepository
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private var userId: Int = -1
    private lateinit var monthSpinner: Spinner
    private lateinit var minGoalEditText: EditText
    private lateinit var maxGoalEditText: EditText

    private val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_goal_setup)

        db = AppDatabase.getDatabase(applicationContext)
        syncRepository = SyncRepository(applicationContext)

        // UI Elements
        val btnSubmit = findViewById<Button>(R.id.button7)
        val btnReset = findViewById<Button>(R.id.button8)
        val btnBack = findViewById<ImageButton>(R.id.imageButton18)
        progressBar = findViewById(R.id.budgetProgressBar)
        progressBar.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.blue))
        progressText = findViewById(R.id.budgetProgressText)

        minGoalEditText = findViewById(R.id.editTextNumberDecimal2)
        maxGoalEditText = findViewById(R.id.editTextNumber)
        monthSpinner = findViewById(R.id.spinner_month)

        monthSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, months)

        btnBack.setOnClickListener {
            startActivity(Intent(this, HomeScreen::class.java))
            finish()
        }

        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        userId = sharedPref.getInt("USER_ID", -1)

        val savedMonth = sharedPref.getString("budget_month", "January") ?: "January"
        val monthIndex = months.indexOf(savedMonth)
        if (monthIndex >= 0) monthSpinner.setSelection(monthIndex)

        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(adapter: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedMonth = months[position]
                sharedPref.edit().putString("budget_month", selectedMonth).apply()
                loadBudgetData(selectedMonth)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSubmit.setOnClickListener {
            val minGoal = minGoalEditText.text.toString().toDoubleOrNull()
            val maxGoal = maxGoalEditText.text.toString().toDoubleOrNull()
            val selectedMonth = monthSpinner.selectedItem.toString()

            if (minGoal == null || maxGoal == null || maxGoal < minGoal) {
                Toast.makeText(this, "Enter valid minimum and maximum values", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                val dao = db.budgetGoalDao()
                val existingGoal = dao.getBudgetForUserAndMonth(userId, selectedMonth)
                val goalId = existingGoal?.goal_id ?: 0

                val goal = BudgetGoal(
                    goal_id = goalId,
                    user_id = userId,
                    month = selectedMonth,
                    minGoal = minGoal,
                    maxGoal = maxGoal
                )

                syncRepository.insertBudgetGoal(goal)
                syncRepository.syncPendingData()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BudgetGoalSetup, "Budget updated successfully", Toast.LENGTH_SHORT).show()
                    loadBudgetData(selectedMonth)
                }
            }
        }

        btnReset.setOnClickListener {
            val selectedMonth = monthSpinner.selectedItem.toString()
            CoroutineScope(Dispatchers.IO).launch {
                val dao = db.budgetGoalDao()
                val goal = dao.getBudgetForUserAndMonth(userId, selectedMonth)
                if (goal != null) {
                    dao.deleteGoal(goal)
                }
                withContext(Dispatchers.Main) {
                    minGoalEditText.text.clear()
                    maxGoalEditText.text.clear()
                    progressBar.progress = 0
                    progressText.text = ""
                    Toast.makeText(this@BudgetGoalSetup, "Budget reset successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadBudgetData(month: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val goalDao = db.budgetGoalDao()
            val expenseDao = db.expenseDao()
            val goal = goalDao.getBudgetForUserAndMonth(userId, month)
            val totalExpenses = expenseDao.getTotalExpensesForUserAndMonth(userId, month) ?: 0.0


            withContext(Dispatchers.Main) {
                if (goal != null) {
                    minGoalEditText.setText(goal.minGoal.toString())
                    maxGoalEditText.setText(goal.maxGoal.toString())
                    updateProgressBar(goal.minGoal, goal.maxGoal, totalExpenses)
                } else {
                    minGoalEditText.setText("")
                    maxGoalEditText.setText("")
                    progressBar.progress = 0
                    progressText.text = "No budget set"
                }
            }
        }
    }

    private fun updateProgressBar(min: Double, max: Double, current: Double) {
        if (max > min) {
            val progress = ((current - min) / (max - min) * 100).toInt().coerceIn(0, 100)
            progressBar.progress = progress
            progressText.text = "Progress: $progress%"
        } else {
            progressBar.progress = 0
            progressText.text = "Invalid goal range"
        }
    }
}
