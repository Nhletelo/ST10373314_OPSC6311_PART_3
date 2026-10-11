package com.example.CoinWatch

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.CoinWatch.data.AppDatabase
import com.example.CoinWatch.data.Category
import com.example.CoinWatch.data.Expense
import com.example.CoinWatch.data.ExpenseDao
import com.example.CoinWatch.data.SyncRepository
import com.example.CoinWatch.databinding.ActivityAddExpenseBinding
import com.example.CoinWatch.scanner.ReceiptScannerActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddExpense : AppCompatActivity() {

    private lateinit var binding: ActivityAddExpenseBinding
    private lateinit var db: AppDatabase
    private lateinit var expenseDao: ExpenseDao
    private lateinit var syncRepository: SyncRepository

    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var receiptScannerLauncher: ActivityResultLauncher<Intent>

    private var capturedPhotoFile: File? = null
    private var capturedPhotoPath: String? = null

    private lateinit var editTextDate: TextView
    private lateinit var editTextTime: TextView
    private lateinit var btnShowDatePicker: Button
    private lateinit var btnPickTime: Button
    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExpenseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        expenseDao = db.expenseDao()
        syncRepository = SyncRepository(this)

        editTextDate = binding.editTextDate
        editTextTime = binding.editTextTime
        btnShowDatePicker = binding.btnShowDatePicker
        btnPickTime = binding.btnPickTime

        // Set default current date and time
        editTextDate.text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
        editTextTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // 1. Advanced Receipt Scanner Launcher (CameraX + AI Analysis & OCR)
        receiptScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val scannedPath = result.data?.getStringExtra("SCANNED_RECEIPT_PATH")
                if (!scannedPath.isNullOrEmpty()) {
                    capturedPhotoPath = scannedPath
                    capturedPhotoFile = File(scannedPath)

                    Glide.with(this)
                        .load(capturedPhotoFile)
                        .into(binding.imgCameraImage)

                    Toast.makeText(this, "Receipt captured. AI processing & categorizing...", Toast.LENGTH_SHORT).show()
                    processReceiptImageWithOCR(capturedPhotoFile!!)

                    if (editTextDate.text.isNull_or_blank()) {
                        editTextDate.text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
                    }
                    if (editTextTime.text.isNull_or_blank()) {
                        editTextTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    }
                }
            }
        }

        // 2. High-resolution Fallback Receipt Camera Launcher
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && capturedPhotoFile != null && capturedPhotoFile!!.exists()) {
                Glide.with(this)
                    .load(capturedPhotoFile)
                    .into(binding.imgCameraImage)

                Toast.makeText(this, "Receipt captured. AI processing & categorizing...", Toast.LENGTH_SHORT).show()
                processReceiptImageWithOCR(capturedPhotoFile!!)

                if (editTextDate.text.isNull_or_blank()) {
                    editTextDate.text = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
                }
                if (editTextTime.text.isNull_or_blank()) {
                    editTextTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                }
            } else {
                Toast.makeText(this, "Receipt photo capture cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        // Camera Permission Launcher
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                receiptScannerLauncher.launch(Intent(this, ReceiptScannerActivity::class.java))
            } else {
                Toast.makeText(this, "Camera permission is required to capture receipts.", Toast.LENGTH_LONG).show()
            }
        }

        binding.imageButton20.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                receiptScannerLauncher.launch(Intent(this, ReceiptScannerActivity::class.java))
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Category Spinner
        val customCategoryEditText = binding.editTextCustomCategory
        customCategoryEditText.visibility = View.GONE

        val spinnerAdapter = ArrayAdapter.createFromResource(
            this, R.array.expense_categories, android.R.layout.simple_spinner_item
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner2.adapter = spinnerAdapter

        binding.spinner2.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCategory = parent?.getItemAtPosition(position).toString()
                if (selectedCategory == "Other" || selectedCategory == "Custom") {
                    customCategoryEditText.visibility = View.VISIBLE
                } else {
                    customCategoryEditText.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Save Expense Button
        binding.button6.setOnClickListener {
            val title = binding.editTextText3.text.toString().ifBlank { "Expense" }
            val description = binding.editTextText4.text.toString()
            val amount = binding.editTextNumberDecimal.text.toString().toDoubleOrNull() ?: 0.0
            val date = editTextDate.text.toString()
            val time = editTextTime.text.toString()
            val selectedCategory = binding.spinner2.selectedItem?.toString() ?: "Other"
            val customCategoryText = customCategoryEditText.text.toString().trim()

            if (date.isBlank() || time.isBlank()) {
                Toast.makeText(this, "Please select date and time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amount <= 0.0) {
                Toast.makeText(this, "Please enter a valid expense amount", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
            val userId = prefs.getInt("USER_ID", -1)

            if (userId == -1) {
                Toast.makeText(this, "User session expired. Please log in.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedMonth = getMonthFromDateString(date)

            lifecycleScope.launch(Dispatchers.IO) {
                val budgetGoalDao = db.budgetGoalDao()

                // Robust budget lookup: check selected month, then current month, then any existing budget goal
                var userBudget = budgetGoalDao.getBudgetForUserAndMonth(userId, selectedMonth)
                if (userBudget == null) {
                    val currentMonth = getCurrentMonth()
                    userBudget = budgetGoalDao.getBudgetForUserAndMonth(userId, currentMonth)
                }
                if (userBudget == null) {
                    userBudget = budgetGoalDao.getGoalsByUser(userId).firstOrNull()
                }

                val totalExpenses = expenseDao.getTotalExpensesForUserAndMonth(userId, selectedMonth) ?: 0.0
                val newTotal = totalExpenses + amount

                val message = if (userBudget != null) {
                    val remaining = userBudget.maxGoal - newTotal
                    if (newTotal > userBudget.maxGoal) {
                        "⚠️ Over budget! Exceeds goal by R%.2f".format(-remaining)
                    } else {
                        "Expense saved & synced! Safe-to-spend left: R%.2f".format(remaining)
                    }
                } else {
                    "Expense saved & synced! (Tip: Set a monthly budget goal in Setup Budget)"
                }

                val categoryDao = db.categoryDao()
                val categoryName = if ((selectedCategory == "Other" || selectedCategory == "Custom") && customCategoryText.isNotEmpty()) {
                    customCategoryText
                } else {
                    selectedCategory
                }

                val existing = categoryDao.getCategoryIdByNameAndUserId(categoryName, userId)
                val categoryId = existing ?: syncRepository.insertCategory(
                    Category(category_name = categoryName, user_id = userId)
                ).toInt()

                val expense = Expense(
                    title = title,
                    description = description,
                    amount = amount,
                    date = date,
                    startTime = time,
                    photoPath = capturedPhotoPath,
                    filePath = null,
                    category_id = categoryId,
                    user_id = userId
                )

                syncRepository.insertExpense(expense)
                syncRepository.syncPendingData()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddExpense, message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@AddExpense, Transactions::class.java))
                    finish()
                }
            }
        }

        binding.imageButton19.setOnClickListener {
            finish()
        }

        btnPickTime.setOnClickListener {
            val cal = Calendar.getInstance()
            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                editTextTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
            }
            TimePickerDialog(this, timeSetListener, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        btnShowDatePicker.setOnClickListener {
            showDatePicker()
        }
    }

    private fun processReceiptImageWithOCR(file: File) {
        try {
            val image = InputImage.fromFilePath(this, Uri.fromFile(file))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText: Text ->
                    val recognizedText = visionText.text
                    val lines = mutableListOf<String>()
                    var extractedAmount: Double? = null
                    var extractedTitle = "Receipt Purchase"
                    var extractedDate: String? = null

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            val text = line.text.trim()
                            if (text.isNotEmpty()) {
                                lines.add(text)
                                val upper = text.uppercase(Locale.getDefault())

                                // 1. Detect Merchant Name
                                if (upper.contains("CHECKERS") || upper.contains("SHOPRITE") || upper.contains("PICK N PAY") ||
                                    upper.contains("WOOLWORTHS") || upper.contains("SPAR") || upper.contains("MAKRO") ||
                                    upper.contains("DIS-CHEM") || upper.contains("CLICKS") || upper.contains("ENGEN") ||
                                    upper.contains("SHELL") || upper.contains("TOTAL") || upper.contains("SASOL") || upper.contains("UBER")) {
                                    if (extractedTitle == "Receipt Purchase") {
                                        extractedTitle = text
                                    }
                                }

                                // 2. Detect Total Amount
                                if (upper.contains("TOTAL") || upper.contains("TOT") || upper.contains("AMOUNT") || upper.contains("CARD") || upper.contains("CASH") || upper.contains("BALANCE")) {
                                    val numbers = Regex("\\d+[.,]\\d{2}").findAll(text)
                                    for (numMatch in numbers) {
                                        val cleanNum = numMatch.value.replace(",", ".")
                                        val amt = cleanNum.toDoubleOrNull()
                                        if (amt != null && amt > 0.0) {
                                            extractedAmount = amt
                                        }
                                    }
                                }

                                // 3. Detect Date
                                val dateRegex = Regex("\\d{4}[-/]\\d{2}[-/]\\d{2}|\\d{2}[-/]\\d{2}[-/]\\d{2,4}")
                                val dateMatch = dateRegex.find(text)
                                if (dateMatch != null && extractedDate == null) {
                                    val rawDate = dateMatch.value.replace("-", "/")
                                    extractedDate = formatToStandardDate(rawDate)
                                }
                            }
                        }
                    }

                    // Fallback amount: max amount on receipt
                    if (extractedAmount == null && lines.isNotEmpty()) {
                        val allAmounts = mutableListOf<Double>()
                        for (line in lines) {
                            val numbers = Regex("\\d+[.,]\\d{2}").findAll(line)
                            for (numMatch in numbers) {
                                val cleanNum = numMatch.value.replace(",", ".")
                                cleanNum.toDoubleOrNull()?.let { if (it > 0.0) allAmounts.add(it) }
                            }
                        }
                        if (allAmounts.isNotEmpty()) {
                            extractedAmount = allAmounts.maxOrNull()
                        }
                    }

                    // Fallback title: first meaningful line
                    if (extractedTitle == "Receipt Purchase") {
                        val firstValidLine = lines.firstOrNull { it.length > 3 && !it.contains("TEL") && !it.contains("VAT") }
                        if (firstValidLine != null) {
                            extractedTitle = firstValidLine
                        }
                    }

                    val detectedCategory = detectCategoryFromText(recognizedText)

                    runOnUiThread {
                        try {
                            if (extractedAmount != null && extractedAmount > 0.0) {
                                binding.editTextNumberDecimal.setText(extractedAmount.toString())
                            }
                            if (extractedTitle.isNotBlank()) {
                                binding.editTextText3.setText(extractedTitle)
                            }
                            if (!extractedDate.isNullOrBlank()) {
                                editTextDate.text = extractedDate
                            }
                            binding.editTextText4.setText("AI Analyzed ($detectedCategory): ${lines.take(2).joinToString(" • ")}")

                            // Select category in spinner safely
                            val adapter = binding.spinner2.adapter
                            if (adapter is ArrayAdapter<*>) {
                                for (i in 0 until adapter.count) {
                                    val item = adapter.getItem(i)?.toString()
                                    if (item.equals(detectedCategory, ignoreCase = true)) {
                                        binding.spinner2.setSelection(i)
                                        break
                                    }
                                }
                            }

                            Toast.makeText(this, "AI OCR: Classified as $detectedCategory (R${extractedAmount ?: 0.0})", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Log.e("ReceiptOCR", "UI update error", e)
                        }
                    }
                }
                .addOnFailureListener { e: Exception ->
                    Log.e("ReceiptOCR", "OCR failed", e)
                    runOnUiThread {
                        Toast.makeText(this, "AI OCR could not read text. Please enter details manually.", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: Exception) {
            Log.e("ReceiptOCR", "Error processing receipt image", e)
        }
    }

    private fun detectCategoryFromText(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            listOf("food", "restaurant", "cafe", "grocery", "market", "supermarket", "coffee", "burger", "pizza", "nando", "kfc", "mcdonald", "shoprite", "checkers", "pick n pay", "woolworths", "spar", "makro", "bakery", "meat", "produce").any { lower.contains(it) } -> "Food"
            listOf("uber", "bolt", "petrol", "fuel", "parking", "shell", "bp", "engen", "total", "sasol", "taxi", "bus", "train", "transport", "car").any { lower.contains(it) } -> "Transport"
            listOf("mall", "store", "clothing", "fashion", "shoes", "zara", "nike", "edgars", "mr price", "shopping", "retail").any { lower.contains(it) } -> "Shopping"
            listOf("electricity", "water", "municipal", "eskom", "wifi", "fiber", "vodacom", "mtn", "telkom", "utility", "bill", "payment").any { lower.contains(it) } -> "Bills"
            listOf("pharmacy", "clicks", "dis-chem", "doctor", "hospital", "medicine", "health", "clinic").any { lower.contains(it) } -> "Health"
            listOf("cinema", "movie", "game", "spotify", "dstv", "entertainment", "ticket").any { lower.contains(it) } -> "Entertainment"
            listOf("hotel", "flight", "booking", "airbnb", "travel", "holiday").any { lower.contains(it) } -> "Travel"
            else -> "Other"
        }
    }

    private fun CharSequence?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(this, { _, year, month, day ->
            val selectedDate = Calendar.getInstance()
            selectedDate.set(year, month, day)
            val formatted = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(selectedDate.time)
            editTextDate.text = formatted
        },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH))
        datePickerDialog.show()
    }

    private fun formatToStandardDate(rawDate: String): String {
        val patterns = arrayOf(
            "yyyy/MM/dd",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "MM/dd/yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(rawDate)
                if (date != null) {
                    return SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                // Try next
            }
        }
        return rawDate
    }

    private fun getMonthFromDateString(dateStr: String): String {
        if (dateStr.isBlank()) return getCurrentMonth()
        val patterns = arrayOf(
            "yyyy/MM/dd",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "MM/dd/yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null) {
                    val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
                    return monthFormat.format(date)
                }
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return getCurrentMonth()
    }

    private fun getCurrentMonth(): String {
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        return monthFormat.format(Calendar.getInstance().time)
    }
}
