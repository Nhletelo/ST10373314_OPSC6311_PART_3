package com.example.CoinWatch

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.widget.AdapterView
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.ExpenseDao
import com.example.CoinWatch.data.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.ItemTouchHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat
import android.Manifest
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.example.CoinWatch.data.ExpenseWithCategory
import com.google.android.material.bottomnavigation.BottomNavigationView

class Transactions : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var expenseDao: ExpenseDao
    private lateinit var syncRepository: SyncRepository
    private val REQUEST_CODE_READ_STORAGE = 101
    private lateinit var totalTextView: TextView
    private lateinit var emptyStateView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transactions)

        totalTextView = findViewById(R.id.totalTextView)
        emptyStateView = findViewById(R.id.emptyStateView)

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        // Set up bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNav.selectedItemId = R.id.transact

        bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.homebutton -> {
                    startActivity(Intent(this, HomeScreen::class.java))
                    true
                }
                R.id.transact -> true
                R.id.areachart -> {
                    startActivity(Intent(this, Chart::class.java))
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

        // Check read storage permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_READ_STORAGE
            )
        }

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)

        val db = AppDatabase.getDatabase(applicationContext)
        expenseDao = db.expenseDao()
        syncRepository = SyncRepository(applicationContext)

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", -1)

        lifecycleScope.launch {
            val expenses = withContext(Dispatchers.IO) {
                expenseDao.getExpensesByUser(userId)
            }

            transactionAdapter = TransactionAdapter(
                expenses,
                onItemClick = { selectedExpense ->
                    val intent = Intent(this@Transactions, TransactionDetailsActivity::class.java).apply {
                        putExtra("title", selectedExpense.expense.title)
                        putExtra("description", selectedExpense.expense.description)
                        putExtra("amount", selectedExpense.expense.amount.toString())
                        putExtra("date", selectedExpense.expense.date)
                        putExtra("startTime", selectedExpense.expense.startTime)
                        putExtra("category", selectedExpense.category.category_name)
                        putExtra("photoPath", selectedExpense.expense.photoPath)
                    }
                    startActivity(intent)
                },
                onEditClick = { selectedExpense ->
                    val intent = Intent(this@Transactions, EditTransactionActivity::class.java).apply {
                        putExtra("expense_id", selectedExpense.expense.expense_id)
                    }
                    startActivity(intent)
                },
                onDeleteClick = { selectedExpense ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            syncRepository.deleteExpenseById(selectedExpense.expense.expense_id)
                            syncRepository.syncPendingData()
                        }
                        val updatedExpenses = withContext(Dispatchers.IO) {
                            expenseDao.getExpensesByUser(userId)
                        }
                        updateListAndEmptyState(updatedExpenses)
                    }
                }
            )
            recyclerView.adapter = transactionAdapter
            updateListAndEmptyState(expenses)

            val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean = false

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val position = viewHolder.adapterPosition
                    val swipedExpense = transactionAdapter.getExpenseAt(position)

                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            syncRepository.deleteExpenseById(swipedExpense.expense.expense_id)
                            syncRepository.syncPendingData()
                        }
                        val updatedExpenses = withContext(Dispatchers.IO) {
                            expenseDao.getExpensesByUser(userId)
                        }
                        updateListAndEmptyState(updatedExpenses)
                    }
                }
            })
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }

        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val dateSpinner = findViewById<Spinner>(R.id.dateSpinner)

        val categoryOptions = listOf("All", "Food", "Transport", "Entertainment", "Health", "Bills", "Shopping", "Custom")
        val dateOptions = listOf("All", "Today", "This Week", "This Month")

        categorySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryOptions)
        dateSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, dateOptions)

        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                filterTransactions(categorySpinner.selectedItem.toString(), dateSpinner.selectedItem.toString())
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        dateSpinner.onItemSelectedListener = categorySpinner.onItemSelectedListener
    }

    private fun filterTransactions(category: String, dateRange: String) {
        val userId = getSharedPreferences("MyAppPrefs", MODE_PRIVATE).getInt("USER_ID", -1)

        lifecycleScope.launch {
            val allExpenses = withContext(Dispatchers.IO) {
                expenseDao.getExpensesByUser(userId)
            }

            val filtered = allExpenses.filter { expense ->
                val matchCategory = category == "All" || expense.category.category_name == category

                val now = System.currentTimeMillis()
                val expenseDateString = expense.expense.date

                val expenseDateMillis = if (!expenseDateString.isNullOrBlank()) {
                    try {
                        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).parse(expenseDateString)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                } else {
                    0L
                }

                val matchDate = when (dateRange) {
                    "Today" -> DateUtils.isToday(expenseDateMillis)
                    "This Week" -> now - expenseDateMillis <= 7 * 24 * 60 * 60 * 1000
                    "This Month" -> {
                        val cal = Calendar.getInstance()
                        val currentMonth = cal.get(Calendar.MONTH)
                        cal.timeInMillis = expenseDateMillis
                        cal.get(Calendar.MONTH) == currentMonth
                    }
                    else -> true
                }

                matchCategory && matchDate
            }

            updateListAndEmptyState(filtered)
        }
    }

    private fun updateListAndEmptyState(list: List<ExpenseWithCategory>) {
        if (::transactionAdapter.isInitialized) {
            transactionAdapter.updateData(list)
        }
        if (list.isEmpty()) {
            emptyStateView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        val totalAmount = list.sumOf { it.expense.amount }
        totalTextView.text = "Total: R${"%.2f".format(totalAmount)}"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        // ...
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Permission", "READ_EXTERNAL_STORAGE permission granted")
            } else {
                Log.d("Permission", "READ_EXTERNAL_STORAGE permission denied")
            }
        }
    }
}
