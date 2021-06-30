package io.github.mojira.arisa.modules

import io.github.mojira.arisa.domain.Attachment

private const val REDACTED_REPLACEMENT = "###REDACTED###"

fun interface AttachmentRedactor {
    fun redact(attachment: Attachment): RedactedAttachment?
}

object AccessTokenRedactor : AttachmentRedactor {
    // Use lookbehind to only redact the token itself
    private val pattern = Regex("""(?<=(^|\s)--accessToken )[a-zA-Z0-9.+/=]+(?=(\s|$))""")

    override fun redact(attachment: Attachment): RedactedAttachment? {
        if (attachment.hasTextContent()) {
            val original = attachment.getTextContent()
            val redacted = original.replace(pattern, REDACTED_REPLACEMENT)
            if (redacted != original) {
                return RedactedAttachment(attachment, redacted)
            }
        }

        return null
    }
}

data class RedactedAttachment(val attachment: Attachment, val redactedContent: String)
