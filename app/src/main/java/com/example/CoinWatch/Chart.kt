package com.example.CoinWatch

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anychart.AnyChart
import com.anychart.AnyChartView
import com.anychart.chart.common.dataentry.ValueDataEntry
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.ExpenseDao
import com.google.android.material.bottomnavigation.BottomNavigationView

import kotlinx.coroutines.launch

class Chart : AppCompatActivity() {

    private lateinit var anyChartView: AnyChartView
    private val categories = arrayOf("Food", "Transport", "Entertainment", "Utilities", "Other")
    private val expenses = intArrayOf(2500, 1800, 1200, 800, 700)
    private lateinit var db: AppDatabase // Your RoomDB class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chart)

        db = AppDatabase.getDatabase(this)

        // ✅ Retrieve userId like you do in HomeScreen
        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", -1)
        Log.d("ChartActivity", "User ID: $userId")

        val expenseDao = db.expenseDao()

        lifecycleScope.launch {
            val expensesByCategory = expenseDao.getTotalExpensesByCategory(userId)
            if (expensesByCategory.isEmpty()) {
                Log.d("ChartActivity", "No expenses found for the user.")
            } else {
                setupChartView(expensesByCategory)
            }
        }

        anyChartView = findViewById(R.id.anyChartView)
        //setupChartView()

        // Set up bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.areachart // Highlight the current item

        bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homebutton -> {
                    startActivity(Intent(this, HomeScreen::class.java))
                    true
                }

                R.id.transact -> {
                    startActivity(Intent(this, Transactions::class.java))
                    true
                }

                R.id.areachart -> {
                    // Already in Chart Layout
                    true
                }

                R.id.expense -> {
                    startActivity(Intent(this, AddExpense::class.java))
                    true
                }

                R.id.profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }


                else -> false
            }
        }
    }

        private fun setupChartView(data: List<ExpenseDao.CategoryExpenseTotal>) {
            val pie = AnyChart.pie()
            val dataEntries = data.map {
                ValueDataEntry(it.category_name, it.total)
            }

            pie.data(dataEntries)
            pie.title("Expenses by Category")
            pie.labels().position("outside")
            pie.legend().title().enabled(true)
            pie.legend().title().text("Categories")
            pie.legend().position("center-bottom")
            pie.legend().itemsLayout("horizontal")
            pie.legend().align("center")
            pie.palette(arrayOf("#FF6384", "#36A2EB", "#FFCE56", "#4BC0C0"))

            anyChartView.setChart(pie)
        }
    }


