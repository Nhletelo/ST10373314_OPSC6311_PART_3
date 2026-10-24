package com.example.CoinWatch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.CoinWatch.data.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class Login : AppCompatActivity() {
    private lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        syncRepository = SyncRepository(this)

        val btnLogin = findViewById<Button>(R.id.button)
        val emailField = findViewById<EditText>(R.id.editTextEmailLogin)
        val passwordField = findViewById<EditText>(R.id.editTextTextPassword)
        val goBackButton = findViewById<ImageButton>(R.id.imageButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarLogin)

        btnLogin.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Signing in..."
            progressBar.visibility = View.VISIBLE

            val hashedPassword = hashPassword(password)

            lifecycleScope.launch(Dispatchers.IO) {
                val user = syncRepository.loginAndSyncUserByEmail(email, hashedPassword)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (user != null) {
                        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        prefs.edit()
                            .putInt("USER_ID", user.user_id)
                            .putString("username", user.username)
                            .apply()

                        val intent = Intent(this@Login, HomeScreen::class.java)
                        intent.putExtra("username", user.username)
                        startActivity(intent)
                        finish()
                    } else {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log In"
                        Toast.makeText(this@Login, "Invalid email or password", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        goBackButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(password.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
