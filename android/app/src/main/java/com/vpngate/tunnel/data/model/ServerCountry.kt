package com.vpngate.tunnel.data.model

enum class ServerCountry(val code: String, val displayName: String, val flagEmoji: String) {
    JAPAN("JP", "Japan", "🇯🇵"),
    UNITED_STATES("US", "United States", "🇺🇸");

    companion object {
        fun fromCode(code: String): ServerCountry? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }
}
