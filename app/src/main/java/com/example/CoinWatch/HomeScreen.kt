package com.example.CoinWatch

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.ExpenseDao
import com.example.CoinWatch.data.ExpenseWithCategory
import com.example.CoinWatch.data.SyncRepository
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.CoinWatch.utils.BudgetAlertManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeScreen : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)

        db = AppDatabase.getDatabase(applicationContext)
        expenseDao = db.expenseDao()
        syncRepository = SyncRepository(applicationContext)

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        var username = prefs.getString("username", null)
        val userId = prefs.getInt("USER_ID", -1)

        val welcomeText = findViewById<TextView>(R.id.textView21)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
        welcomeText.text = if (!username.isNullOrBlank()) "$greeting, $username" else greeting

        if (userId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                val dbUser = db.userDao().getUserById(userId)
                if (dbUser != null) {
                    username = dbUser.username
                    prefs.edit().putString("username", username).apply()
                    withContext(Dispatchers.Main) {
                        welcomeText.text = "$greeting, $username"
                    }
                }
                syncRepository.syncPendingData()
                syncRepository.pullUserDataFromSupabase(userId)
                RecurringTransactionsActivity.processDueRecurringTransactions(applicationContext, userId)
                withContext(Dispatchers.Main) {
                    updateDashboard(userId)
                }
            }
        } else if (intent.hasExtra("username")) {
            val newUsername = intent.getStringExtra("username") ?: "Guest"
            prefs.edit().putString("username", newUsername).apply()
            welcomeText.text = "$greeting, $newUsername"
        }

        updateDashboard(userId)

        if (userId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                syncRepository.syncPendingData()
                syncRepository.pullUserDataFromSupabase(userId)
                RecurringTransactionsActivity.processDueRecurringTransactions(applicationContext, userId)
                withContext(Dispatchers.Main) {
                    updateDashboard(userId)
                }
            }
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.homebutton
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homebutton -> true
                R.id.transact -> {
                    startActivity(Intent(this, Transactions::class.java))
                    true
                }
                R.id.areachart -> {
                    startActivity(Intent(this, Chart::class.java))
                    true
                }
                R.id.expense -> {
                    startActivity(Intent(this, AddExpense::class.java))
                    true
                }
                R.id.incomes -> {
                    startActivity(Intent(this, IncomesActivity::class.java))
                    true
                }
                else -> false
            }
        }

        findViewById<ImageButton>(R.id.imageButton21).setOnClickListener {
            startActivity(Intent(this, SetupBudget::class.java))
        }
        findViewById<ImageButton>(R.id.imageButton22).setOnClickListener {
            startActivity(Intent(this, BudgetGoalSetup::class.java))
        }
        findViewById<ImageButton>(R.id.imageButtonAiCoach).setOnClickListener {
            startActivity(Intent(this, SmartFinancialAdvisorActivity::class.java))
        }
        findViewById<ImageButton>(R.id.imageButtonMessaging).setOnClickListener {
            startActivity(Intent(this, SupportMessageActivity::class.java))
        }

        findViewById<View>(R.id.btnAddIncomeQuick)?.setOnClickListener {
            startActivity(Intent(this, AddIncomeActivity::class.java))
        }

        findViewById<View>(R.id.btnOpenProfile)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.cardRecurringBills)?.setOnClickListener {
            startActivity(Intent(this, RecurringTransactionsActivity::class.java))
        }

        findViewById<View>(R.id.cardAiCoachBanner)?.setOnClickListener {
            startActivity(Intent(this, SmartFinancialAdvisorActivity::class.java))
        }


    }

    private fun updateDashboard(userId: Int) {
        if (userId == -1) return

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)

        val tvIncome = findViewById<TextView>(R.id.tvIncomeAmount)
        val tvBudget = findViewById<TextView>(R.id.tvBudgetAmount)
        val tvSpent = findViewById<TextView>(R.id.spentAmountTextView)
        val tvHighestCategory = findViewById<TextView>(R.id.tvHighestCategory)
        val tvOverspentStatus = findViewById<TextView>(R.id.tvOverspentStatus)

        CoroutineScope(Dispatchers.IO).launch {
            val dbIncome = db.incomeDao().getTotalIncomeByUser(userId) ?: 0.0
            val prefsIncome = prefs.getFloat("USER_INCOME_$userId", 0.0f).toDouble()
            val income = if (dbIncome > 0.0) dbIncome else prefsIncome

            val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
            val budgetGoal = db.budgetGoalDao().getBudgetForUserAndMonth(userId, currentMonth)
            val maxBudget = budgetGoal?.maxGoal ?: 0.0

            val allExpenses = expenseDao.getExpensesByUser(userId)
            val periodSpent = allExpenses.sumOf { it.expense.amount }

            BudgetAlertManager.checkBudgetAndNotify(applicationContext, userId)

            // Highest category
            val categorySums = allExpenses.groupBy { it.category.category_name }
                .mapValues { entry -> entry.value.sumOf { it.expense.amount } }
            val highestCatEntry = categorySums.maxByOrNull { it.value }
            val highestCategoryText = if (highestCatEntry != null) {
                "${highestCatEntry.key}: R${"%.2f".format(highestCatEntry.value)}"
            } else {
                "None"
            }

            withContext(Dispatchers.Main) {
                tvIncome.text = "R%.2f".format(income)
                tvBudget.text = "R%.2f".format(maxBudget)
                tvSpent.text = "R%.2f".format(periodSpent)
                tvHighestCategory.text = highestCategoryText

                val recurringList = db.recurringTransactionDao().getRecurringTransactionsByUser(userId)
                    .filter { it.is_active }
                    .sortedBy { it.next_due_date }

                val upcomingText = if (recurringList.isNotEmpty()) {
                    val next = recurringList.first()
                    "Next: ${next.title} (R${"%.2f".format(next.amount)}) due on ${next.next_due_date}"
                } else {
                    "No active recurring bills or subscriptions"
                }
                findViewById<TextView>(R.id.tvUpcomingBillSummary)?.text = upcomingText

                if (income > 0.0) {
                    val diff = income - periodSpent
                    if (diff < 0) {
                        val overspentAmount = -diff
                        tvOverspentStatus.text = "OVERSPENT BY R%.2f!".format(overspentAmount)
                        tvOverspentStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
                    } else {
                        tvOverspentStatus.text = "Safe-to-Spend Balance: R%.2f".format(diff)
                        tvOverspentStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E7D32"))
                    }
                } else {
                    tvOverspentStatus.text = "Set Monthly Income in Add Income or Setup Budget"
                    tvOverspentStatus.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#26FFFFFF"))
                }

                val aiInsightText = when {
                    maxBudget > 0 && periodSpent > maxBudget -> "You have exceeded your monthly budget. Tap here to view AI Coach recommendations for budget recovery."
                    maxBudget > 0 && (periodSpent / maxBudget) >= 0.8 -> "You have used over 80% of your monthly budget. Tap here for AI advice on saving."
                    highestCatEntry != null -> "Your highest expense is in ${highestCatEntry.key} (R${"%.2f".format(highestCatEntry.value)}). Tap here for AI insights."
                    else -> "Welcome! Add your income and expenses to unlock personalized AI financial coaching."
                }
                findViewById<TextView>(R.id.tvAiCoachBannerText)?.text = aiInsightText
            }
        }
    }

    private fun parseDateMillis(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", -1)
        updateDashboard(userId)
        if (userId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                syncRepository.syncPendingData()
                syncRepository.pullUserDataFromSupabase(userId)
                RecurringTransactionsActivity.processDueRecurringTransactions(applicationContext, userId)
                withContext(Dispatchers.Main) {
                    updateDashboard(userId)
                }
            }
        }
    }
}
