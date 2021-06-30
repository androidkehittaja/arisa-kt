package io.github.mojira.arisa.modules

import arrow.core.Either
import arrow.core.extensions.fx
import com.urielsalis.mccrashlib.deobfuscator.getSafeChildPath
import io.github.mojira.arisa.domain.Attachment
import io.github.mojira.arisa.domain.CommentOptions
import io.github.mojira.arisa.domain.Issue
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Instant

private val log = LoggerFactory.getLogger("PrivacyModule")

class PrivacyModule(
    private val message: String,
    private val commentNote: String,
    private val allowedEmailsRegex: List<Regex>,
    private val attachmentRedactor: AttachmentRedactor,
    private val sensitiveFileNames: List<String>
) : Module {
    private val patterns: List<Regex> = listOf(
        """.*\(Session ID is token:.*""".toRegex(),
        """.*--accessToken ey.*""".toRegex(),
        """.*(?<![^\s])(?=[^\s]*[A-Z])(?=[^\s]*[0-9])[A-Z0-9]{17}(?![^\s]).*""".toRegex(),
        // At the moment braintree transaction IDs seem to have 8 chars, but to be future-proof
        // match if there are more chars as well
        """.*\bbraintree:[a-f0-9]{6,12}\b.*""".toRegex(),
        """.*\b([A-Za-z0-9]{4}-){3}[A-Za-z0-9]{4}\b.*""".toRegex()
    )

    private val emailRegex = "(?<!\\[~)\\b[a-zA-Z0-9.\\-_]+@[a-zA-Z.\\-_]+\\.[a-zA-Z.\\-]{2,15}\\b".toRegex()

    override fun invoke(issue: Issue, lastRun: Instant): Either<ModuleError, ModuleResponse> = with(issue) {
        Either.fx {
            assertNull(securityLevel).bind()

            var string = ""

            if (created.isAfter(lastRun)) {
                string += "$summary $environment $description "
            }

            var attachmentContainsSensitiveData = false
            val attachmentsToRedact = attachments
                .filter { it.created.isAfter(lastRun) }
                .filter { it.hasTextContent() }
                .mapNotNull {
                    // Don't redact bot attachments to guard against infinite loop
                    // But still check bot attachments for sensitive data, e.g. when deobfuscated crash report
                    // contains sensitive data
                    val redacted = if (it.uploader.isBotUser()) null else attachmentRedactor.redact(it)
                    if (redacted == null) {
                        // No redaction necessary / possible; check if attachment contains sensitive data
                        if (!attachmentContainsSensitiveData) {
                            attachmentContainsSensitiveData = containsSensitiveData(it.getTextContent())
                        }
                        return@mapNotNull null
                    } else {
                        // Check if attachment content still contains sensitive data after redacting
                        if (containsSensitiveData(redacted.redactedContent)) {
                            attachmentContainsSensitiveData = true
                            return@mapNotNull null
                        }
                        return@mapNotNull redacted
                    }
                }

            var containsIssueSensitiveData = attachmentContainsSensitiveData
            if (!containsIssueSensitiveData) {
                changeLog
                    .filter { it.created.isAfter(lastRun) }
                    .filter { it.field != "Attachment" }
                    .filter { it.changedFromString == null }
                    .forEach { string += "${it.changedToString} " }

                containsIssueSensitiveData = containsSensitiveData(string)
            }

            val doesAttachmentNameMatch = attachments
                .asSequence()
                .map(Attachment::name)
                .any(sensitiveFileNames::contains)

            val restrictCommentFunctions = comments
                .asSequence()
                .filter { it.created.isAfter(lastRun) }
                .filter { it.visibilityType == null }
                .filter { it.body?.let(::containsSensitiveData) ?: false }
                .filterNot {
                    it.getAuthorGroups()?.any { group ->
                        listOf("helper", "global-moderators", "staff").contains(group)
                    } ?: false
                }
                .map { { it.restrict("${it.body}$commentNote") } }
                .toList()

            assertEither(
                assertTrue(attachmentsToRedact.isNotEmpty()),
                assertTrue(containsIssueSensitiveData),
                assertTrue(doesAttachmentNameMatch),
                assertNotEmpty(restrictCommentFunctions)
            ).bind()

            // Always try to redact attachments, even if issue would be made private anyways
            // So in case issue was made private erroneously it can easily be made public
            val redactedAll = redactAttachments(issue, attachmentsToRedact)

            if (!redactedAll || containsIssueSensitiveData || doesAttachmentNameMatch) {
                setPrivate()
                addComment(CommentOptions(message))
            }

            restrictCommentFunctions.forEach { it.invoke() }
        }
    }

    private fun containsSensitiveData(string: String) =
        matchesEmail(string) || patterns.any { it.containsMatchIn(string) }

    private fun matchesEmail(string: String): Boolean {
        return emailRegex
            .findAll(string)
            .filterNot { email -> allowedEmailsRegex.any { regex -> regex.matches(email.value) } }
            .any()
    }

    private fun redactAttachments(issue: Issue, attachments: Collection<RedactedAttachment>): Boolean {
        var redactedAll = true
        attachments
            // Group by uploader in case they uploaded multiple attachments at once
            .groupBy { it.attachment.uploader.name!! }
            .forEach { (uploader, userAttachments) ->
                val fileNames = mutableSetOf<String>()
                userAttachments.forEach {
                    val attachment = it.attachment
                    val tempDir = Files.createTempDirectory("arisa-redaction-upload").toFile()
                    val fileName = "redacted_${attachment.name}"
                    val filePath = getSafeChildPath(tempDir, fileName)

                    if (filePath == null || !fileNames.add(fileName)) {
                        redactedAll = false
                        // Note: Don't log file name to avoid log injection
                        log.warn("Attachment with ID ${attachment.id} of issue ${issue.key} has malformed or duplicate file name")
                        tempDir.delete()
                    } else {
                        filePath.writeText(it.redactedContent)
                        issue.addAttachment(filePath) {
                            // Once uploaded, delete the temp directory containing the attachment
                            tempDir.deleteRecursively()
                        }
                        attachment.remove()
                    }
                }

                if (fileNames.isNotEmpty()) {
                    val fileNamesString = fileNames.joinToString("\n- ", prefix = "\n- ") {
                        // Use link for attachments
                        "[^$it]"
                    }
                    // TODO: Use helper message; use getMessageWithBotSignature
                    // TODO: Don't restrict comment, make it public
                    issue.addRawRestrictedComment(
                        "@[~$uploader], sensitive data has been removed from your attachment(s) " +
                                "and they have been re-uploaded as:$fileNamesString",
                        "helper"
                    )
                }
            }

        return redactedAll
    }
}
