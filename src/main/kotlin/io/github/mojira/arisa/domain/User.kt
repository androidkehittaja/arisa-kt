package io.github.mojira.arisa.domain

data class User(
    // TODO: Can they really be null?
    val name: String?,
    val displayName: String?,
    val getGroups: () -> List<String>?,
    val isNewUser: () -> Boolean,
    val isBotUser: () -> Boolean,
)
