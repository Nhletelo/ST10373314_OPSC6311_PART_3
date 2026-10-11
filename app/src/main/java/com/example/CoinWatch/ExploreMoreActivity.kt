package com.example.CoinWatch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.CoinWatch.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ExploreMoreActivity : AppCompatActivity() {

    private lateinit var tipTextView: TextView
    private lateinit var btnNextTip: Button
    private lateinit var btnRefreshInsights: Button

    private val realTimeTipsList = mutableListOf<String>()
    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())
    private val autoCycleRunnable = object : Runnable {
        override fun run() {
            if (realTimeTipsList.isNotEmpty()) {
                showNextTip()
            }
            handler.postDelayed(this, 10_000) // Every 10 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explore_more)

        tipTextView = findViewById(R.id.text_ai_advice)
        btnNextTip = findViewById(R.id.btnNextTip)
        btnRefreshInsights = findViewById(R.id.btnRefreshInsights)

        val backButton = findViewById<ImageButton>(R.id.button_Back)
        backButton.setOnClickListener {
            finish()
        }

        btnNextTip.setOnClickListener {
            showNextTip()
        }

        btnRefreshInsights.setOnClickListener {
            loadRealTimeDatabaseInsights()
        }

        loadRealTimeDatabaseInsights()
        handler.postDelayed(autoCycleRunnable, 10_000)
    }

    private fun loadRealTimeDatabaseInsights() {
        tipTextView.text = "⚡ Analyzing your real-time expense data..."

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", -1)

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val expenseDao = db.expenseDao()
            val budgetDao = db.budgetGoalDao()

            val currentMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date())
            val expenses = expenseDao.getExpensesByUser(userId)
            val categoryTotals = expenseDao.getTotalExpensesByCategory(userId)
            val totalSpent = expenseDao.getTotalSpentByUser(userId) ?: 0.0
            val userBudget = budgetDao.getBudgetForUserAndMonth(userId, currentMonth)

            val generatedTips = mutableListOf<String>()

            // 1. Real-time Top Category Insight
            if (categoryTotals.isNotEmpty()) {
                val topCategory = categoryTotals.maxByOrNull { it.total }
                if (topCategory != null && topCategory.total > 0) {
                    generatedTips.add(
                        "📊 REAL-TIME ANALYSIS:\nYour highest spending category is ${topCategory.category_name} (R${"%.2f".format(topCategory.total)}).\n\n💡 Tip: Cooking 2 more meals at home or reducing non-essential ${topCategory.category_name} buys could save you over R300/month!"
                    )
                }
            }

            // 2. Real-time Budget Usage Alert
            if (userBudget != null && userBudget.maxGoal > 0) {
                val percentUsed = (totalSpent / userBudget.maxGoal) * 100
                val formattedPercent = "%.1f".format(percentUsed)
                val formattedSpent = "%.2f".format(totalSpent)
                val formattedBudget = "%.2f".format(userBudget.maxGoal)

                if (percentUsed >= 90) {
                    generatedTips.add(
                        "🚨 REAL-TIME BUDGET WARNING:\nYou have spent $formattedPercent% of your monthly budget (R$formattedSpent / R$formattedBudget).\n\n💡 Tip: Hold off on non-essential purchases for the rest of $currentMonth to stay within budget!"
                    )
                } else if (percentUsed >= 50) {
                    generatedTips.add(
                        "📈 REAL-TIME BUDGET UPDATE:\nYou've used $formattedPercent% of your $currentMonth budget (R$formattedSpent / R$formattedBudget).\n\n💡 Tip: Try taking on a '3-Day No-Spend Challenge' this week to preserve your remaining goal!"
                    )
                } else {
                    generatedTips.add(
                        "✅ REAL-TIME BUDGET STATUS:\nYou are doing great! You've used $formattedPercent% of your monthly budget (R$formattedSpent / R$formattedBudget).\n\n💡 Tip: Deposit any leftover cash into savings at the end of the month!"
                    )
                }
            }

            // 3. Real-time Transaction Count & Frequency
            if (expenses.isNotEmpty()) {
                val totalCount = expenses.size
                val smallTxCount = expenses.count { it.expense.amount in 1.0..50.0 }
                generatedTips.add(
                    "⚡ REAL-TIME SPENDING HABIT:\nYou have logged $totalCount transactions ($smallTxCount micro-purchases under R50).\n\n💡 Tip: Small daily buys (< R50) often accumulate to over R400/month. Keep tracking every transaction!"
                )
            } else {
                generatedTips.add(
                    "💡 REAL-TIME TIP:\nNo transactions logged yet this month! Start by adding your daily transport or grocery expense to see live spending analytics."
                )
            }

            // General Money Saving Hacks
            generatedTips.add(
                "⏳ 24-HOUR RULE:\nBefore making non-essential buys over R200, wait 24 hours. Most impulse urges fade away!"
            )
            generatedTips.add(
                "☕ COFFEE & TAKEOUT HACK:\nMaking coffee or tea at home saves approx. R30 daily — that adds up to R900 back in your pocket every month!"
            )
            generatedTips.add(
                "📱 SUBSCRIPTION CLEANUP:\nAudit streaming or app subscriptions. Cancel any service you haven't used in 30 days to free up cash immediately."
            )

            withContext(Dispatchers.Main) {
                realTimeTipsList.clear()
                realTimeTipsList.addAll(generatedTips)
                currentIndex = 0
                showCurrentTip()
            }
        }
    }

    private fun showNextTip() {
        if (realTimeTipsList.isEmpty()) return
        currentIndex = (currentIndex + 1) % realTimeTipsList.size
        showCurrentTip()
    }

    private fun showCurrentTip() {
        if (realTimeTipsList.isNotEmpty() && currentIndex in realTimeTipsList.indices) {
            tipTextView.text = realTimeTipsList[currentIndex]
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoCycleRunnable)
    }
}
