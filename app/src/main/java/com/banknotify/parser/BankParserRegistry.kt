package com.banknotify.parser

import com.banknotify.core.model.Transaction

object BankParserRegistry {

    private val configs = listOf(
        BankParserConfig("VCB", "Vietcombank", listOf("com.vietcombank", "com.vietcombank.vcb", "vcb.app.com.vcb"), listOf("VCB", "Vietcombank")),
        BankParserConfig("TCB", "Techcombank", listOf("com.techcombank", "com.techcombank.techcombankapp", "vn.techcombank"), listOf("Techcombank", "TCB")),
        BankParserConfig("MB", "MB Bank", listOf("com.msb.android", "com.mbbank", "com.mb.mbbank"), listOf("MB", "MBBank")),
        BankParserConfig("ACB", "ACB", listOf("com.acb.acb", "vn.acb", "com.acb.app"), listOf("ACB")),
        BankParserConfig("VPB", "VPBank", listOf("com.vpbank", "vn.vpbank", "com.vpbank.app"), listOf("VPBank", "VPB")),
        BankParserConfig("TPB", "TPBank", listOf("com.tpbank", "vn.tpbank", "com.tpbank.app"), listOf("TPBank", "TPB")),
        BankParserConfig("VIB", "VIB", listOf("com.vib", "vn.vib", "com.vib.app"), listOf("VIB")),
        BankParserConfig("BIDV", "BIDV", listOf("com.bidv", "vn.bidv", "com.bidv.app"), listOf("BIDV")),
        BankParserConfig("CTG", "VietinBank", listOf("com.vietinbank", "vn.vietinbank", "com.vietinbank.app"), listOf("VietinBank", "CTG")),
        BankParserConfig("STB", "Sacombank", listOf("com.sacombank", "vn.sacombank", "com.sacombank.app"), listOf("Sacombank", "STB")),
        BankParserConfig("HDB", "HDBank", listOf("com.hdbank", "vn.hdbank", "com.hdbank.app"), listOf("HDBank", "HDB")),
        BankParserConfig("OCB", "OCB", listOf("com.ocb", "vn.ocb", "com.ocb.app", "com.ocb.ocbapp"), listOf("OCB")),
        BankParserConfig("MSB", "MSB", listOf("com.msb", "vn.msb", "com.msb.app"), listOf("MSB")),
        BankParserConfig("SHB", "SHB", listOf("com.shb", "vn.shb", "com.shb.app"), listOf("SHB"))
    )

    private val parsers: List<BankParser> = configs.map { BaseBankParser(it) }

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
