package com.banknotify.parser

data class BankParserConfig(
    val bankCode: String,
    val bankName: String,
    val packageNames: List<String>,
    val identifiers: List<String>,
    val accountType: String = "BANK"
)
