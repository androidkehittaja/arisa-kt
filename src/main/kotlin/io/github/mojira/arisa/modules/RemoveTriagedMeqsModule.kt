package io.github.mojira.arisa.modules

import arrow.core.Either
import arrow.core.extensions.fx
import arrow.core.left
import arrow.core.right
import io.github.mojira.arisa.domain.Issue
import java.time.Instant

class RemoveTriagedMeqsModule(
    private val meqsTags: List<String>,
    private val removalReason: String
) : Module() {
    override fun execute(issue: Issue, lastRun: Instant): Either<ModuleError, ModuleResponse> = with(issue) {
        Either.fx {
            assertTriaged(priority, triagedTime).bind()

            val updateMeqsComments = comments
                .filter { hasMeqsTag(it.body) }
                .map { it.update.partially1(removeMeqsTags(it.body!!)) }
            assertNotEmpty(updateMeqsComments).bind()

            updateMeqsComments.forEach { it.invoke() }
        }
    }

    private fun hasMeqsTag(comment: String?) =
        meqsTags.any { comment?.contains(it) ?: false }

    private fun removeMeqsTags(comment: String): String {
        val regex = (
                "MEQS(" +
                        meqsTags.joinToString("|") { it.replace("MEQS", "") } +
                        ")"
                ).toRegex()
        return regex.replace(comment) { "MEQS_ARISA_REMOVED${it.groupValues[1]} Removal Reason: $removalReason" }
    }

    private fun assertTriaged(priority: String?, triagedTime: String?) = when {
        priority == null && triagedTime == null -> OperationNotNeededModuleResponse.left()
        else -> Unit.right()
    }
}
