package com.banknotify.parser

import com.banknotify.core.model.Transaction

object BankParserRegistry {

    private val parsers: List<BankParser> = listOf(
        VietcombankParser(), TechcombankParser(), MBBankParser(), ACBParser(),
        VPBankParser(), TPBankParser(), VIBParser(), BIDVParser(),
        VietinBankParser(), SacombankParser(), HDBankParser(), OCBParser(),
        MSBParser(), SHBParser()
    )

    private val packageMap: Map<String, BankParser> = parsers.flatMap { p ->
        p.packageNames.map { it.lowercase() to p }
    }.toMap()

    fun getParserForPackage(packageName: String): BankParser? = packageMap[packageName.lowercase()]

    fun getAllParsers(): List<BankParser> = parsers

    fun parse(packageName: String, title: String, body: String): Transaction? {
        val direct = getParserForPackage(packageName)
        if (direct != null) {
            try { direct.parse(title, body)?.let { return it } } catch (_: Exception) {}
        }
        for (p in parsers) {
            if (p.packageNames.none { it.lowercase() == packageName.lowercase() }) {
                try { p.parse(title, body)?.let { return it } } catch (_: Exception) {}
            }
        }
        return null
    }
}
