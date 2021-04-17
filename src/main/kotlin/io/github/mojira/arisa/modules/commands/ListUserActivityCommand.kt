package io.github.mojira.arisa.modules.commands

import io.github.mojira.arisa.domain.Issue
import io.github.mojira.arisa.domain.service.IssueService

/**
 * How many tickets should be listed at max.
 * This is a safety guard in case the command gets invoked on a very active user.
 * We don't want the comment to become too long.
 */
const val ACTIVITY_LIST_CAP = 50 // TODO readd me

class ListUserActivityCommand(
    val issueService: IssueService
) {
    operator fun invoke(issue: Issue, userName: String): Int {
        val escapedUserName = userName.replace("'", "\\'")

        val jql = """issueFunction IN commented("by '$escapedUserName'")
            | OR issueFunction IN fileAttached("by '$escapedUserName'")"""
            .trimMargin().replace("[\n\r]", "")

        val tickets = issueService.searchIssues(jql)
        if (tickets.isEmpty()) {
            throw CommandExceptions.CANNOT_QUERY_USER_ACTIVITY.create(userName)
        }

        if (tickets.isNotEmpty()) {
            issue.addRawComment(
                "User \"$userName\" left comments on the following tickets:\n* ${tickets.joinToString("\n* ")}",
                "group",
                "staff"
            )
        } else {
            issue.addRawComment(
                """No unrestricted comments from user "$userName" were found.""",
                "group",
                "staff"
            )
        }

        return 1
    }
}
