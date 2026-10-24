package com.example.CoinWatch

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.Category
import com.example.CoinWatch.data.SyncRepository
import com.example.CoinWatch.databinding.ActivityEditTransactionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTransactionBinding
    private var expenseId: Int = -1
    private var userId: Int = -1
    private lateinit var db: AppDatabase
    private lateinit var syncRepository: SyncRepository
    private var categoriesList: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        syncRepository = SyncRepository(this)

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        userId = prefs.getInt("USER_ID", -1)

        val btnBack = findViewById<ImageButton>(R.id.imageButton26)
        btnBack.setOnClickListener {
            finish()
        }

        expenseId = intent.getIntExtra("expense_id", -1)

        if (expenseId != -1) {
            loadCategoriesAndExpense()
        } else {
            Toast.makeText(this, "Invalid expense ID", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnUpdateExpense.setOnClickListener {
            updateExpense()
        }
    }

    private fun loadCategoriesAndExpense() {
        lifecycleScope.launch(Dispatchers.IO) {
            categoriesList = db.categoryDao().getCategoriesByUser(userId)
            val categoryNames = categoriesList.map { it.category_name }

            val expense = db.expenseDao().getExpenseById(expenseId)

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(this@EditTransactionActivity, android.R.layout.simple_spinner_dropdown_item, categoryNames)
                binding.spinnerEditCategory.adapter = adapter

                expense?.let {
                    binding.editTitle.setText(it.title)
                    binding.editDescription.setText(it.description)
                    binding.editAmount.setText(it.amount.toString())
                    binding.editDate.setText(it.date)
                    binding.editTime.setText(it.startTime)

                    // Select current category in spinner
                    if (it.category_id != null) {
                        val matchingCategory = categoriesList.find { cat -> cat.category_id == it.category_id }
                        if (matchingCategory != null) {
                            val index = categoryNames.indexOf(matchingCategory.category_name)
                            if (index >= 0) {
                                binding.spinnerEditCategory.setSelection(index)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateExpense() {
        val updatedTitle = binding.editTitle.text.toString().trim()
        val updatedDescription = binding.editDescription.text.toString().trim()
        val updatedAmount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val updatedDate = binding.editDate.text.toString().trim()
        val updatedTime = binding.editTime.text.toString().trim()

        val selectedCategoryPosition = binding.spinnerEditCategory.selectedItemPosition
        val selectedCategoryId = if (selectedCategoryPosition in categoriesList.indices) {
            categoriesList[selectedCategoryPosition].category_id
        } else {
            null
        }

        if (updatedTitle.isEmpty()) {
            binding.editTitle.error = "Title cannot be empty"
            return
        }

        if (updatedAmount <= 0.0) {
            binding.editAmount.error = "Enter a valid amount"
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val expense = db.expenseDao().getExpenseById(expenseId)
            if (expense != null) {
                val updatedExpense = expense.copy(
                    title = updatedTitle,
                    description = updatedDescription,
                    amount = updatedAmount,
                    date = updatedDate,
                    startTime = updatedTime,
                    category_id = selectedCategoryId
                )
                syncRepository.updateExpense(updatedExpense)
                syncRepository.syncPendingData()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTransactionActivity, "Transaction & category updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
