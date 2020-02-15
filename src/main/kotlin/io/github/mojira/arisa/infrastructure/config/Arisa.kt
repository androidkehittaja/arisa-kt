package io.github.mojira.arisa.infrastructure.config

import com.uchuhimo.konf.ConfigSpec

object Arisa : ConfigSpec() {
    object Credentials : ConfigSpec() {
        val username by required<String>()
        val password by required<String>()
    }

    object Issues : ConfigSpec() {
        val projects by optional("MC,MCTEST,MCPE,MCAPI,MCL,MC")
        val url by optional("https://bugs.mojang.com/")
        val checkInterval by optional(10L)
    }

    object CustomFields : ConfigSpec() {
        val chkField by optional("customfield_10701")
        val confirmationField by optional("customfield_10500")
    }

    object Modules : ConfigSpec() {
        object Attachment : ConfigSpec() {
            val extensionBlacklist by optional("jar,exe,com,bat,msi,run,lnk,dmg")
        }
    }
}
