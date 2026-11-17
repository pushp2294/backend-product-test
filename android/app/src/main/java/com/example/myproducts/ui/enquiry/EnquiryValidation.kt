package com.example.myproducts.ui.enquiry

/** The form's fields, named as the server names them in VALIDATION_ERROR details[].field. */
enum class EnquiryField(val serverName: String) {
    Name("name"),
    Email("email"),
    Phone("phone"),
    Message("message"),
    ;

    companion object {
        fun fromServerName(name: String): EnquiryField? = entries.firstOrNull { it.serverName == name }
    }
}

/** What the customer has typed so far. */
data class EnquiryForm(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val message: String = "",
)

// A deliberately simple shape check: something@something.something, no spaces. The
// server's validation is authoritative; this only catches obvious typos before sending.
private val EMAIL_SHAPE = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")
private val PHONE_CHARS = Regex("""^[0-9+\- ]*$""")

/**
 * Client-side checks that mirror the backend's rules (backend/src/http/schemas.ts), so most
 * mistakes are shown instantly and never cost a request. Returns field -> message; empty
 * means the form can be sent. Messages match the server's own wording.
 */
fun validateEnquiry(form: EnquiryForm): Map<EnquiryField, String> = buildMap {
    val name = form.name.trim()
    when {
        name.isEmpty() -> put(EnquiryField.Name, "Enter your name")
        name.length > 100 -> put(EnquiryField.Name, "Name must be at most 100 characters")
    }

    val email = form.email.trim()
    if (email.length > 254 || !EMAIL_SHAPE.matches(email)) {
        put(EnquiryField.Email, "Enter a valid email address")
    }

    val phone = form.phone.trim()
    when {
        phone.length > 30 -> put(EnquiryField.Phone, "Phone must be at most 30 characters")
        !PHONE_CHARS.matches(phone) -> put(EnquiryField.Phone, "Use digits, spaces, + or - only")
    }

    if (form.message.trim().length > 1000) {
        put(EnquiryField.Message, "Message must be at most 1000 characters")
    }
}
