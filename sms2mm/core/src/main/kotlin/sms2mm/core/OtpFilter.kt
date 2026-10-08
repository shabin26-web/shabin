package sms2mm.core

/**
 * Recognises OTP, verification and password messages so they are dropped before
 * any parsing or storage happens.
 *
 * Deliberately over-cautious: a real transaction SMS that happens to mention an OTP
 * is also dropped. It is reported as [SmsOutcome.Sensitive] (bank + time only), so the
 * user can still add it by hand.
 */
object OtpFilter {
    private val PATTERNS = listOf(
        Regex("""\bOTP\b""", RegexOption.IGNORE_CASE),
        Regex("""one[\s-]?time\s+(pass(word|code)?|pin|code)""", RegexOption.IGNORE_CASE),
        Regex("""verification\s+code""", RegexOption.IGNORE_CASE),
        Regex("""(activation|security|authentication|login)\s+code""", RegexOption.IGNORE_CASE),
        Regex("""do\s+not\s+share""", RegexOption.IGNORE_CASE),
        Regex("""\bpassword\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPIN\b"""),
        Regex("رمز"),            // code (رمز التحقق, رمز الدخول, ...)
        Regex("كلمة\\s*(ال)?مرور"), // password
        Regex("لا\\s*تشارك"),       // do not share
        Regex("التحقق"),           // verification
    )

    fun isSensitive(body: String): Boolean = PATTERNS.any { it.containsMatchIn(body) }
}
