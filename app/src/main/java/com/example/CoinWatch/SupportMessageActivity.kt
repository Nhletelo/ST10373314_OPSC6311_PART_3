package com.example.CoinWatch

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.CoinWatch.data.SyncRepository
import com.example.CoinWatch.data.SupportTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SupportMessageActivity : AppCompatActivity() {

    private lateinit var syncRepository: SyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_support_message)

        syncRepository = SyncRepository(this)

        val emailEditText = findViewById<EditText>(R.id.editTextEmail)
        val phoneEditText = findViewById<EditText>(R.id.editTextPhone)
        val messageEditText = findViewById<EditText>(R.id.editTextMessage)
        val sendButton = findViewById<Button>(R.id.buttonSend)
        val backButton = findViewById<Button>(R.id.buttonBack)

        val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userId = prefs.getInt("USER_ID", -1)

        backButton.setOnClickListener {
            finish()
        }

        sendButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()
            val message = messageEditText.text.toString().trim()

            if (email.isBlank() || phone.isBlank() || message.isBlank()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())

            lifecycleScope.launch(Dispatchers.IO) {
                val ticket = SupportTicket(
                    user_id = if (userId != -1) userId else 0,
                    email = email,
                    phone = phone,
                    message = message,
                    date = currentDate
                )
                syncRepository.submitSupportTicket(ticket)
                syncRepository.syncPendingData()

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@SupportMessageActivity,
                        "Support ticket submitted & synced successfully! We will assist you shortly.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }
}
