package com.banknotify.parser

import com.banknotify.model.Transaction

interface BankParser {
    val bankCode: String
    val bankName: String
    val packageNames: List<String>
    fun parse(title: String, body: String): Transaction?
}
