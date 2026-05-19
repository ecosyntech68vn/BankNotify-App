package com.banknotify.parser

import com.banknotify.model.Transaction

object BankParserRegistry {

    private val parsers: List<BankParser> = listOf(
        VietcombankParser(),
        TechcombankParser(),
        MBBankParser(),
        ACBParser(),
        VPBankParser(),
        TPBankParser(),
        VIBParser(),
        BIDVParser(),
        VietinBankParser(),
        SacombankParser(),
        HDBankParser(),
        OCBParser(),
        MSBParser(),
        SHBParser()
    )

    private val packageMap: Map<String, BankParser> = parsers.flatMap { parser ->
        parser.packageNames.map { pkg -> pkg.lowercase() to parser }
    }.toMap()

    fun getParserForPackage(packageName: String): BankParser? {
        return packageMap[packageName.lowercase()]
    }

    fun getAllParsers(): List<BankParser> = parsers

    fun parse(packageName: String, title: String, body: String): Transaction? {
        val parser = getParserForPackage(packageName)
        if (parser != null) {
            try {
                val result = parser.parse(title, body)
                if (result != null) return result
            } catch (_: Exception) {}
        }

        for (p in parsers) {
            try {
                if (p.packageNames.none { it.lowercase() == packageName.lowercase() }) {
                    val result = p.parse(title, body)
                    if (result != null) return result
                }
            } catch (_: Exception) {}
        }

        return null
    }
}
