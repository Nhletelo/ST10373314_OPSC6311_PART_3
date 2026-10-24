# CoinWatch - Personal Finance & Budget Tracker

**CoinWatch** is a personal finance tracker designed for Android (Kotlin, SDK 35). Built with a local-first Room database and synchronized with a Supabase PostgreSQL cloud backend, CoinWatch equips users with smart AI receipt scanning, recurring bill automation, proactive financial coaching, and custom monthly budgeting.

---

## Key Features

### 1. Smart AI Receipt Scanner (ML Kit OCR)
* **CameraX & Gallery Input:** Capture receipts using live camera feed (with flashlight toggle) or upload receipt images directly from the gallery.
* **Automatic Field Extraction:** ML Kit Text Recognition automatically extracts Merchant/Vendor, Date, Total Amount, Tax/VAT, and Category.
* **Review & Correction Workflow:** Extracted fields populate editable inputs, allowing users to verify or adjust values before saving.

### 2. Cloud Synchronization & Local Persistence
* **Offline-First Architecture:** Powered by Room local database so the app works offline.
* **Supabase Cloud Sync:** Syncs transactions, incomes, categories, budget goals, and support tickets with Supabase PostgreSQL when online.
* **MailerSend OTP Password Reset:** Password reset links and 6-digit OTP codes delivered via MailerSend REST API.

### 3. Monthly Incomes & Expenses CRUD
* **Income Management:** Create, list, filter by month, edit, and delete monthly income entries.
* **Expense Tracking:** Log expenses manually or via AI receipt scanner with category classification and date filtering.
* **Safe-to-Spend Balance:** Automatically deducts total expenses from total income to display the exact safe-to-spend balance.

### 4. Recurring Transactions & Subscriptions
* **Automated Processing:** Automatically checks due dates on app startup and logs recurring bills or regular incomes when due.
* **Tomorrow-Onward Scheduling:** Prevents invalid same-day duplicates by requiring new recurring bills to be scheduled starting from tomorrow.

### 5. Proactive AI Financial Coach & Budget Alerts
* **Dashboard Proactive Insights:** Live AI banner on the dashboard analyzing spending habits, budget limits, and top categories.
* **Budget Alert Notifications:** High-priority local notifications sent at 80% budget usage warning and 100% budget overspending.

### 6. Modern Material 3 UI/UX & South African Rand (R) Currency
* **Material 3 Design:** Human-designed layouts with clean typography, hairline dividers, and state-aware interactions.
* **South African Rand (`R`):** Tailored for South African currency formatting (`R`).
* **Account Management:** Edit display name, log out, or delete user account with cloud cleanup.

---

## Tech Stack & Architecture

* **Language:** Kotlin (Android SDK 35)
* **UI Toolkit:** Android Views (XML Layouts), Material Components (Material 3), Jetpack Compose
* **Local Database:** Room Database (SQLite)
* **Cloud Backend:** Supabase Kotlin SDK (`postgrest-kt`, `auth-kt`)
* **Email & OTP Delivery:** MailerSend REST API via Ktor Client
* **AI & OCR:** ML Kit Text Recognition (`com.google.mlkit:text-recognition`)
* **Camera & Media:** CameraX (`androidx.camera`), Glide Image Loading
* **Analytics & Charts:** AnyChart Pie & Area Charts
* **Asynchronous Execution:** Kotlin Coroutines & StateFlow/Flow

---

## Getting Started

1. **Clone & Open:** Open the project in Android Studio (2024.2+ recommended).
2. **Configuration:**
   - Ensure `google-services.json` is placed inside the `app/` directory.
   - Verify Supabase credentials in `SupabaseClient.kt`.
3. **Build & Run:**
   - Command line: `./gradlew app:assembleDebug`
   - Run on an Android Device or Emulator running API 27+.

---

## License

Copyright © 2025 CoinWatch. All rights reserved.
