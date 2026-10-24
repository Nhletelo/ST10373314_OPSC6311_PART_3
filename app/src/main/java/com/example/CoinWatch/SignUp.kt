package com.example.CoinWatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.SyncRepository
import com.example.CoinWatch.data.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class SignUp : AppCompatActivity() {
    private lateinit var db: AppDatabase
    private lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        db = AppDatabase.getDatabase(this)
        syncRepository = SyncRepository(this)

        val btnSignup = findViewById<Button>(R.id.button2)
        val goBackButton = findViewById<ImageButton>(R.id.imageButton2)

        btnSignup.setOnClickListener {
            val editTextUsername = findViewById<EditText>(R.id.editTextText)
            val editTextEmail = findViewById<EditText>(R.id.editTextEmail)
            val editTextPassword = findViewById<EditText>(R.id.editTextTextPassword2)
            val editConfirmPassword = findViewById<EditText>(R.id.editTextTextPassword3)

            val username = editTextUsername.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val password = editTextPassword.text.toString()
            val confirmPassword = editConfirmPassword.text.toString()

            if (username.isEmpty()) {
                editTextUsername.error = "Username cannot be empty"
                return@setOnClickListener
            }

            if (email.isEmpty() || !email.contains("@")) {
                editTextEmail.error = "Please enter a valid email address"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                editTextPassword.error = "Password cannot be empty"
                return@setOnClickListener
            }

            if (confirmPassword.isEmpty()) {
                editConfirmPassword.error = "Confirm Password cannot be empty"
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                editConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            btnSignup.isEnabled = false
            btnSignup.text = "Creating account..."

            CoroutineScope(Dispatchers.IO).launch {
                val existingUser = db.userDao().getUserByEmail(email)
                if (existingUser != null) {
                    withContext(Dispatchers.Main) {
                        btnSignup.isEnabled = true
                        btnSignup.text = "Sign Up"
                        Toast.makeText(this@SignUp, "Email already exists", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val hashedPassword = hashPassword(password)
                    val newUser = User(username = username, email = email, password = hashedPassword)
                    syncRepository.insertUser(newUser)
                    syncRepository.syncPendingData()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SignUp, "Account created successfully", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@SignUp, Login::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }

        goBackButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(password.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
