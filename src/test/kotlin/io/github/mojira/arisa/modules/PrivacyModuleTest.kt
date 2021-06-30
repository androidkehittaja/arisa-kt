package io.github.mojira.arisa.modules

import io.github.mojira.arisa.utils.RIGHT_NOW
import io.github.mojira.arisa.utils.mockAttachment
import io.github.mojira.arisa.utils.mockChangeLogItem
import io.github.mojira.arisa.utils.mockComment
import io.github.mojira.arisa.utils.mockIssue
import io.github.mojira.arisa.utils.mockUser
import io.kotest.assertions.arrow.either.shouldBeLeft
import io.kotest.assertions.arrow.either.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private val ALLOWED_REGEX = listOf("allowed@.*".toRegex())
private val NOOP_REDACTOR = AttachmentRedactor { null }
private val MODULE = PrivacyModule(
    "message",
    "\n----\nRestricted by PrivacyModule ??[~arisabot]??",
    ALLOWED_REGEX,
    NOOP_REDACTOR,
    emptyList()
)
private val TWO_SECONDS_AGO = RIGHT_NOW.minusSeconds(2)
private val TEN_SECONDS_AGO = RIGHT_NOW.minusSeconds(10)

class PrivacyModuleTest : StringSpec({
    "should return OperationNotNeededModuleResponse when the ticket is marked as private" {
        val issue = mockIssue(
            securityLevel = "private"
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the ticket is created before lastRun" {
        val issue = mockIssue(
            created = TEN_SECONDS_AGO,
            description = "foo@example.com"
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the comment is created before lastRun" {
        val issue = mockIssue(
            comments = listOf(
                mockComment(
                    body = "foo@example.com",
                    created = TEN_SECONDS_AGO
                )
            )
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the comment is not public" {
        val issue = mockIssue(
            comments = listOf(
                mockComment(
                    body = "foo@example.com",
                    visibilityType = "group",
                    visibilityValue = "helper"
                )
            )
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the attachment is created before lastRun" {
        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    created = TEN_SECONDS_AGO,
                    getContent = { "foo@example.com".toByteArray() }
                )
            )
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the attachment is not a text file" {
        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    mimeType = "image/png",
                    getContent = { "foo@example.com".toByteArray() }
                )
            )
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the change log item is created before lastRun" {
        val issue = mockIssue(
            changeLog = listOf(
                mockChangeLogItem(
                    created = TEN_SECONDS_AGO,
                    changedFromString = null,
                    changedToString = "foo@example.com"
                )
            )
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should return OperationNotNeededModuleResponse when the ticket doesn't match the patterns" {
        val issue = mockIssue(
            summary = "Test"
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should mark as private when the summary contains Email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            summary = "foo_bar@example.com",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the Email address contains dots" {
        var hasSetPrivate = false

        val issue = mockIssue(
            summary = "f.o.o@example.com",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the Email address uses .cc tld" {
        var hasSetPrivate = false

        val issue = mockIssue(
            summary = "foo@example.cc",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the Email address uses .americanexpress tld" {
        var hasSetPrivate = false

        val issue = mockIssue(
            summary = "foo@example.americanexpress",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the environment contains Email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            environment = "foo@example.com",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the description contains Email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            description = "foo@example.com",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the description contains braintree transaction ID" {
        var hasSetPrivate = false

        val issue = mockIssue(
            description = "My transaction id: *braintree:1a2b3c4d*",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the attachment contains Email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    getContent = { "foo@example.com".toByteArray() }
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should return OperationNotNeededModuleResponse when the email address is contained in a user mention" {
        val issue = mockIssue(
            summary = "[~foo@example.com]"
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
    }

    "should mark as private when the Email is contained in a link" {
        var hasSetPrivate = false

        val issue = mockIssue(
            description = "[foo@example.com|mailto:foo@example.com]",
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the attachment contains session ID" {
        var hasSetPrivate = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    getContent = { "(Session ID is token:My1_hnfNSd3nyQ7IbbnGbTS1fgJuM6JkfH2WEKaTTOLPc)".toByteArray() }
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the attachment contains access token" {
        var hasSetPrivate = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    getContent = { "--uuid 1312dkkdk2kdart342 --accessToken eyJimfake.12345.fakestuff --userType mojang".toByteArray() }
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should mark as private when the change log item contains email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            changeLog = listOf(
                mockChangeLogItem(
                    field = "environment",
                    changedFromString = null,
                    changedToString = "My email is foo@example.com."
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should not mark as private when the change log item contains an allowed email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            changeLog = listOf(
                mockChangeLogItem(
                    field = "environment",
                    changedFromString = null,
                    changedToString = "My email is allowed@example.com."
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
        hasSetPrivate shouldBe false
    }

    "should mark as private when the change log item contains an allowed email and a not allowed email" {
        var hasSetPrivate = false

        val issue = mockIssue(
            changeLog = listOf(
                mockChangeLogItem(
                    field = "environment",
                    changedFromString = null,
                    changedToString = "My email is allowed@example.com but I also use foo@example.com."
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should restrict to staff when the comment contains Email" {
        var hasSetPrivate = false
        var hasRestrictedComment = false

        val issue = mockIssue(
            comments = listOf(
                mockComment(
                    body = "foo@example.com",
                    restrict = {
                        hasRestrictedComment = true
                        it shouldBe "foo@example.com\n----\nRestricted by PrivacyModule ??[~arisabot]??"
                    }
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = MODULE(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe false
        hasRestrictedComment shouldBe true
    }

    "should mark as private when attachment has sensitive name" {
        val sensitiveFileName = "sensitive.txt"
        val module = PrivacyModule("message", "comment", ALLOWED_REGEX, NOOP_REDACTOR, listOf(sensitiveFileName))
        var hasSetPrivate = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    name = sensitiveFileName
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = module(issue, TWO_SECONDS_AGO)

        result.shouldBeRight(ModuleResponse)
        hasSetPrivate shouldBe true
    }

    "should not mark as private when attachment has non-sensitive name" {
        val sensitiveFileName = "sensitive.txt"
        val module = PrivacyModule("message", "comment", ALLOWED_REGEX, NOOP_REDACTOR, listOf(sensitiveFileName))
        var hasSetPrivate = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    name = "non-$sensitiveFileName"
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = module(issue, TWO_SECONDS_AGO)

        result.shouldBeLeft(OperationNotNeededModuleResponse)
        hasSetPrivate shouldBe false
    }

    "should redact access tokens" {
        val module = PrivacyModule("message", "comment", ALLOWED_REGEX, AccessTokenRedactor, emptyList())
        val uploader = "some-user"
        val attachmentName = "my-attachment.txt"

        var hasSetPrivate = false
        var hasDeletedAttachment = false
        lateinit var newAttachmentName: String
        lateinit var newAttachmentContent: String
        lateinit var addedComment: String

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    name = attachmentName,
                    uploader = mockUser(
                        name = uploader
                    ),
                    // Example JWT token from https://jwt.io/
                    getContent = { "... --accessToken eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c ...".toByteArray() },
                    remove = { hasDeletedAttachment = true }
                )
            ),
            setPrivate = { hasSetPrivate = true },
            addAttachment = { file, cleanupCallback ->
                newAttachmentName = file.name
                newAttachmentContent = file.readText()
                cleanupCallback()
            },
            addRawRestrictedComment = { comment, _ -> addedComment = comment }
        )

        val result = module(issue, TWO_SECONDS_AGO)
        result.shouldBeRight(ModuleResponse)

        // Attachment was redacted; issue should not have been made private
        hasSetPrivate shouldBe false
        hasDeletedAttachment shouldBe true
        newAttachmentName shouldBe "redacted_$attachmentName"
        newAttachmentContent shouldBe "... --accessToken ###REDACTED### ..."

        addedComment shouldContain uploader
        addedComment shouldContain newAttachmentName
    }

    "should not redact bot attachments" {
        val module = PrivacyModule("message", "comment", ALLOWED_REGEX, AccessTokenRedactor, emptyList())
        var hasSetPrivate = false
        var hasDeletedAttachment = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    uploader = mockUser(
                        isBotUser = { true }
                    ),
                    // Example JWT token from https://jwt.io/
                    getContent = { "... --accessToken eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c ...".toByteArray() },
                    remove = { hasDeletedAttachment = true }
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = module(issue, TWO_SECONDS_AGO)
        result.shouldBeRight(ModuleResponse)

        hasSetPrivate shouldBe true
        hasDeletedAttachment shouldBe false
    }

    "should not redact attachments with non-redactable content" {
        val module = PrivacyModule("message", "comment", ALLOWED_REGEX, AccessTokenRedactor, emptyList())

        var hasSetPrivate = false
        var hasDeletedAttachment = false

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    // Redactor does not handle this; but it represents sensitive data
                    getContent = { "My transaction ID is braintree:1a2b3c4d".toByteArray() },
                    remove = { hasDeletedAttachment = true }
                )
            ),
            setPrivate = { hasSetPrivate = true }
        )

        val result = module(issue, TWO_SECONDS_AGO)
        result.shouldBeRight(ModuleResponse)

        hasSetPrivate shouldBe true
        hasDeletedAttachment shouldBe false
    }

    "should redact attachments even if issue is made private" {
        val madePrivateMessage = "message"
        val module = PrivacyModule(madePrivateMessage, "comment", ALLOWED_REGEX, AccessTokenRedactor, emptyList())

        var hasSetPrivate = false
        var hasDeletedAttachment = false
        var hasAddedNewAttachment = false
        val addedComments = mutableListOf<String>()

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    // Example JWT token from https://jwt.io/
                    getContent = { "... --accessToken eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c ...".toByteArray() },
                    remove = { hasDeletedAttachment = true }
                )
            ),
            setPrivate = { hasSetPrivate = true },
            addAttachment = { _, cleanupCallback ->
                hasAddedNewAttachment = true
                cleanupCallback()
            },
            addRawRestrictedComment = { comment, _ -> addedComments.add(comment) }
        )

        val result = module(issue, TWO_SECONDS_AGO)
        result.shouldBeRight(ModuleResponse)

        hasSetPrivate shouldBe true
        // Issue was made private, but attachment should have been redacted anyways
        hasDeletedAttachment shouldBe true
        hasAddedNewAttachment shouldBe true
        addedComments shouldContain madePrivateMessage
        addedComments shouldContain "" // TODO
    }

    "should make private if redacting does not remove all sensitive data" {
        val madePrivateMessage = "message"
        val module = PrivacyModule(madePrivateMessage, "comment", ALLOWED_REGEX, AccessTokenRedactor, emptyList())

        var hasSetPrivate = false
        var hasDeletedAttachment = false
        val addedComments = mutableListOf<String>()

        val issue = mockIssue(
            attachments = listOf(
                mockAttachment(
                    // Example JWT token from https://jwt.io/
                    getContent = { (
                        "... --accessToken eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c ...\n"
                        // This cannot be redacted
                        + "My transaction ID is braintree:1a2b3c4d"
                    ).toByteArray() },
                    remove = { hasDeletedAttachment = true }
                )
            ),
            setPrivate = { hasSetPrivate = true },
            addRawRestrictedComment = { comment, _ -> addedComments.add(comment) }
        )

        val result = module(issue, TWO_SECONDS_AGO)
        result.shouldBeRight(ModuleResponse)

        hasSetPrivate shouldBe true
        hasDeletedAttachment shouldBe false
        addedComments shouldContainExactly listOf(madePrivateMessage)
    }
})
