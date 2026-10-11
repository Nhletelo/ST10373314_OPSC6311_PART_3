package com.example.CoinWatch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Transaction
    @Query("SELECT * FROM expenses WHERE user_id = :userId")
    fun getExpensesByUser(userId: Int): List<ExpenseWithCategory>

    @Transaction
    @Query("SELECT * FROM expenses WHERE user_id = :userId")
    fun observeExpensesByUser(userId: Int): Flow<List<ExpenseWithCategory>>

    @Query("SELECT * FROM expenses INNER JOIN categories ON expenses.category_id = categories.category_id")
    fun getAllExpensesWithCategory(): List<ExpenseWithCategory>

    @Query("SELECT * FROM expenses WHERE expense_id = :expenseId LIMIT 1")
    suspend fun getExpenseById(expenseId: Int): Expense?

    @Query("SELECT SUM(amount) FROM expenses WHERE user_id = :userId")
    suspend fun getTotalSpentByUser(userId: Int): Double?

    @Query("SELECT SUM(amount) FROM expenses WHERE user_id = :userId AND date LIKE :datePrefix || '%'")
    suspend fun getTotalExpensesForUserAndMonth(userId: Int, datePrefix: String): Double?

    @Query("""
        SELECT categories.category_name, SUM(expenses.amount) as total 
        FROM expenses 
        INNER JOIN categories ON expenses.category_id = categories.category_id 
        WHERE expenses.user_id = :userId 
        GROUP BY categories.category_id
    """)
    suspend fun getTotalExpensesByCategory(userId: Int): List<CategoryExpenseTotal>

    data class CategoryExpenseTotal(
        val category_name: String,
        val total: Double
    )

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses WHERE expense_id = :expenseId")
    suspend fun deleteExpenseById(expenseId: Int)

    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun getUnsyncedExpenses(): List<Expense>

    @Query("UPDATE expenses SET is_synced = 1 WHERE expense_id = :expenseId")
    suspend fun markExpenseSynced(expenseId: Int)
}
