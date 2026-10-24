package com.example.CoinWatch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getUserById(userId: Int): User?

    @Query("SELECT * FROM users WHERE username = :username AND password = :password")
    suspend fun login(username: String, password: String): User?

    @Query("SELECT * FROM users WHERE email = :email AND password = :password")
    suspend fun loginByEmail(email: String, password: String): User?

    @Query("SELECT * FROM users WHERE is_synced = 0")
    suspend fun getUnsyncedUsers(): List<User>

    @Query("UPDATE users SET is_synced = 1 WHERE user_id = :userId")
    suspend fun markUserSynced(userId: Int)

    @Query("UPDATE users SET password = :newPassword, is_synced = 0 WHERE email = :email")
    suspend fun updatePasswordByEmail(email: String, newPassword: String)

    @Query("UPDATE users SET username = :username, is_synced = 0 WHERE user_id = :userId")
    suspend fun updateUsername(userId: Int, username: String)

    @Query("DELETE FROM users WHERE user_id = :userId")
    suspend fun deleteUserById(userId: Int)
}
