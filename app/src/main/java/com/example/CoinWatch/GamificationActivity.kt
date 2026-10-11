package com.example.CoinWatch

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class GamificationActivity : AppCompatActivity() {

    private lateinit var tvPoints: TextView
    private lateinit var tvQuestion: TextView
    private lateinit var rgOptions: RadioGroup
    private lateinit var btnSubmit: Button
    private lateinit var ivBadge: ImageView
    private lateinit var tvLevel: TextView
    private lateinit var progressBarQuiz: ProgressBar
    private lateinit var tvProgressLabel: TextView

    private var currentPoints = 0
    private var currentQuestionIndex = 0

    private val questions = listOf(
        QuizQuestion("What is the best way to save money?", listOf("Spend less than you earn", "Buy on credit", "Avoid budgets", "Ignore bills"), 0),
        QuizQuestion("Which one is a fixed expense?", listOf("Rent", "Eating out", "Movies", "Clothes"), 0),
        QuizQuestion("What should you do before investing?", listOf("Research the option", "Guess the market", "Ask friends only", "Ignore risks"), 0),
        QuizQuestion("What is a budget?", listOf("A plan for spending money", "A type of loan", "Credit card limit", "Savings account"), 0),
        QuizQuestion("Why save money?", listOf("For emergencies", "To spend more", "To avoid banks", "To borrow more"), 0),
        QuizQuestion("What is interest?", listOf("Money earned on savings", "A fee for borrowing", "A type of budget", "A bill to pay"), 1),
        QuizQuestion("What does debt mean?", listOf("Money you owe", "Money you own", "Money saved", "Money gifted"), 0),
        QuizQuestion("Which is a variable expense?", listOf("Electricity bill", "Rent", "Car loan", "Mortgage"), 0),
        QuizQuestion("What is an emergency fund?", listOf("Money saved for unexpected costs", "A credit card", "A type of insurance", "A bank loan"), 0),
        QuizQuestion("What is a good financial habit?", listOf("Tracking expenses", "Ignoring bills", "Spending impulsively", "Avoiding budgets"), 0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gamification)

        findViewById<ImageButton>(R.id.btnBackGame).setOnClickListener {
            finish()
        }

        tvPoints = findViewById(R.id.tvPoints)
        tvQuestion = findViewById(R.id.tvQuestion)
        rgOptions = findViewById(R.id.rgOptions)
        btnSubmit = findViewById(R.id.btnSubmit)
        ivBadge = findViewById(R.id.ivBadge)
        tvLevel = findViewById(R.id.tvLevel)
        progressBarQuiz = findViewById(R.id.progressBarQuiz)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)

        currentPoints = getPointsFromPrefs()
        updatePointsDisplay()
        updateBadgeAndLevel()
        loadQuestion()

        btnSubmit.setOnClickListener {
            if (btnSubmit.text.toString() == "Play Again") {
                currentQuestionIndex = 0
                btnSubmit.text = "Submit Answer"
                loadQuestion()
                return@setOnClickListener
            }

            val selectedId = rgOptions.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "Please select an answer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedIndex = when (selectedId) {
                R.id.rbOption1 -> 0
                R.id.rbOption2 -> 1
                R.id.rbOption3 -> 2
                R.id.rbOption4 -> 3
                else -> -1
            }

            if (selectedIndex != -1) {
                checkAnswer(selectedIndex)
            }
        }
    }

    private fun loadQuestion() {
        if (currentQuestionIndex >= questions.size) {
            return
        }

        val question = questions[currentQuestionIndex]
        tvQuestion.text = question.question
        tvProgressLabel.text = "Question ${currentQuestionIndex + 1} of ${questions.size}"
        progressBarQuiz.progress = ((currentQuestionIndex + 1).toFloat() / questions.size * 100).toInt()

        for (i in 0 until rgOptions.childCount) {
            val rb = rgOptions.getChildAt(i) as RadioButton
            if (i < question.options.size) {
                rb.text = question.options[i]
                rb.visibility = android.view.View.VISIBLE
            } else {
                rb.visibility = android.view.View.GONE
            }
        }

        rgOptions.clearCheck()
    }

    private fun checkAnswer(selectedIndex: Int) {
        val correctIndex = questions[currentQuestionIndex].correctAnswerIndex
        if (selectedIndex == correctIndex) {
            currentPoints += 10
            Toast.makeText(this, "Correct! +10 points 🎉", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Incorrect. The correct answer was: ${questions[currentQuestionIndex].options[correctIndex]}", Toast.LENGTH_LONG).show()
        }

        savePointsToPrefs(currentPoints)
        updatePointsDisplay()
        updateBadgeAndLevel()

        currentQuestionIndex++

        if (currentQuestionIndex >= questions.size) {
            btnSubmit.text = "Play Again"
            tvQuestion.text = "🏆 Quiz Completed! You scored $currentPoints total points."
            tvProgressLabel.text = "Completed"
            progressBarQuiz.progress = 100
            Toast.makeText(this, "Quiz finished! Great job!", Toast.LENGTH_LONG).show()
        } else {
            loadQuestion()
        }
    }

    private fun updatePointsDisplay() {
        tvPoints.text = "Points: $currentPoints"
    }

    private fun updateBadgeAndLevel() {
        val badgeResId = getBadgeResource(currentPoints)
        val level = getLevel(currentPoints)

        ivBadge.setImageResource(badgeResId)
        tvLevel.text = "Level: $level"
    }

    private fun getBadgeResource(points: Int): Int {
        return when {
            points >= 100 -> R.drawable.badge_gold
            points >= 60 -> R.drawable.badge_silver
            points >= 30 -> R.drawable.badge_bronze
            else -> R.drawable.badge_none
        }
    }

    private fun getLevel(points: Int): String {
        return when {
            points >= 100 -> "Financial Master (Gold)"
            points >= 60 -> "Budget Expert (Silver)"
            points >= 30 -> "Saver (Bronze)"
            else -> "Beginner"
        }
    }

    private fun savePointsToPrefs(points: Int) {
        val prefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("USER_POINTS", points).apply()
    }

    private fun getPointsFromPrefs(): Int {
        val prefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("USER_POINTS", 0)
    }

    data class QuizQuestion(
        val question: String,
        val options: List<String>,
        val correctAnswerIndex: Int
    )
}
