package com.example.CoinWatch

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.SyncRepository
import com.example.CoinWatch.databinding.ActivityEditTransactionBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditTransactionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTransactionBinding
    private var expenseId: Int = -1
    private lateinit var db: AppDatabase
    private lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        syncRepository = SyncRepository(this)

        val btnBack = findViewById<ImageButton>(R.id.imageButton26)
        btnBack.setOnClickListener {
            val intent = Intent(this, Transactions::class.java)
            startActivity(intent)
        }

        expenseId = intent.getIntExtra("expense_id", -1)

        if (expenseId != -1) {
            loadExpenseData()
        } else {
            Toast.makeText(this, "Invalid expense ID", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnUpdateExpense.setOnClickListener {
            updateExpense()
        }
    }

    private fun loadExpenseData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val expense = db.expenseDao().getExpenseById(expenseId)
            expense?.let {
                withContext(Dispatchers.Main) {
                    binding.editTitle.setText(it.title)
                    binding.editDescription.setText(it.description)
                    binding.editAmount.setText(it.amount.toString())
                    binding.editDate.setText(it.date)
                    binding.editTime.setText(it.startTime)
                }
            }
        }
    }

    private fun updateExpense() {
        val updatedTitle = binding.editTitle.text.toString()
        val updatedDescription = binding.editDescription.text.toString()
        val updatedAmount = binding.editAmount.text.toString().toDoubleOrNull() ?: 0.0
        val updatedDate = binding.editDate.text.toString()
        val updatedTime = binding.editTime.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val expense = db.expenseDao().getExpenseById(expenseId)
            if (expense != null) {
                val updatedExpense = expense.copy(
                    title = updatedTitle,
                    description = updatedDescription,
                    amount = updatedAmount,
                    date = updatedDate,
                    startTime = updatedTime
                )
                syncRepository.updateExpense(updatedExpense)
                syncRepository.syncPendingData()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditTransactionActivity, "Expense updated & synced", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
