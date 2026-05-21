package com.banknotify.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BaseBankParserTest {

    private val vcbParser = BankParserRegistry.getParserForPackage("com.vietcombank")!!
    private val tcbParser = BankParserRegistry.getParserForPackage("com.techcombank")!!

    @Test
    fun `parse VCB notification with all fields`() {
        val title = "VCB - Bao mat"
        val body = "Tai khoan: 1012345678\nSo tien: +500,000 VND\nNoi dung: Chuyen tien mua hang\n" +
                "So du: 5,000,000 VND\nGD: REF123456\nNghiep vu: Chuyen khoan\n" +
                "NGUYEN VAN A chuyen khoan"
        val tx = vcbParser.parse(title, body)

        assertThat(tx).isNotNull()
        assertThat(tx!!.bankCode).isEqualTo("VCB")
        assertThat(tx.bankName).isEqualTo("Vietcombank")
        assertThat(tx.accountNumber).isEqualTo("1012345678")
        assertThat(tx.amount).isEqualTo(500000.0)
        assertThat(tx.balance).isEqualTo(5000000.0)
        assertThat(tx.content).contains("Chuyen tien mua hang")
        assertThat(tx.senderName).contains("NGUYEN VAN A")
        assertThat(tx.referenceNumber).isEqualTo("REF123456")
    }

    @Test
    fun `parse VCB notification with negative amount`() {
        val body = "Tai khoan: 1012345678\nSo tien: -200,000 VND\nNoi dung: Rut tien\n" +
                "So du: 1,000,000 VND"
        val tx = vcbParser.parse("VCB", body)

        assertThat(tx).isNotNull()
        assertThat(tx!!.amount).isEqualTo(-200000.0)
        assertThat(tx.content).isEqualTo("Rut tien")
    }

    @Test
    fun `parse Techcombank notification`() {
        val body = "Tai khoan: 2022334455\nSo tien: +1,000,000 VND\nNoi dung: Thanh toan hoa don\n" +
                "So du: 10,000,000 VND"
        val tx = tcbParser.parse("Techcombank", body)

        assertThat(tx).isNotNull()
        assertThat(tx!!.bankCode).isEqualTo("TCB")
        assertThat(tx.amount).isEqualTo(1000000.0)
    }

    @Test
    fun `return null when no bank identifier found`() {
        val body = "Some random notification without bank name"
        val tx = vcbParser.parse("Unknown App", body)
        assertThat(tx).isNull()
    }

    @Test
    fun `return null when no amount found`() {
        val body = "VCB - Tai khoan: 1012345678\nNoi dung: No amount here"
        val tx = vcbParser.parse("VCB", body)
        assertThat(tx).isNull()
    }

    @Test
    fun `parse amount with decimal`() {
        val body = "VCB - So tien: 1,500.50 VND\nTai khoan: 1012345678\nNoi dung: Test"
        val tx = vcbParser.parse("VCB", body)
        assertThat(tx).isNotNull()
        assertThat(tx!!.amount).isEqualTo(1500.50)
    }

    @Test
    fun `parse amount with large number`() {
        val body = "VCB - So tien: +100,000,000 VND\nTai khoan: 1012345678\nNoi dung: Test"
        val tx = vcbParser.parse("VCB", body)
        assertThat(tx).isNotNull()
        assertThat(tx!!.amount).isEqualTo(100000000.0)
    }

    @Test
    fun `parse notification with only required fields`() {
        val body = "So tien: 50,000 VND\nTai khoan: 1012345678\nVCB"
        val tx = vcbParser.parse("VCB", body)
        assertThat(tx).isNotNull()
        assertThat(tx!!.amount).isEqualTo(50000.0)
        assertThat(tx.balance).isNull()
        assertThat(tx.senderName).isNull()
        assertThat(tx.referenceNumber).isNull()
    }

    @Test
    fun `parse sender with special Vietnamese characters`() {
        val body = "So tien: 100,000 VND\nTai khoan: 1012345678\n" +
                "TRẦN VĂN BẢO chuyen khoan\nNoi dung: Test\nVCB"
        val tx = vcbParser.parse("VCB", body)
        assertThat(tx).isNotNull()
        assertThat(tx!!.senderName).contains("TRẦN VĂN BẢO")
    }

    @Test
    fun `parse with mixed content`() {
        val body = """
            VCB - Bao mat
            Tai khoan: 1912345678
            So tien: +2,500,000 VND
            Noi dung: CT tu 1012345678 NGUYEN THI B
            So du: 15,000,000 VND
            GD: ABCD123456
            Thoi gian: 15/05/2026 14:30
        """.trimIndent()
        val tx = vcbParser.parse("VCB", body)
        assertThat(tx).isNotNull()
        assertThat(tx!!.amount).isEqualTo(2500000.0)
        assertThat(tx.accountNumber).isEqualTo("1912345678")
        assertThat(tx.referenceNumber).isEqualTo("ABCD123456")
    }

    @Test
    fun `return null for non-matching package`() {
        val acbParser = BankParserRegistry.getParserForPackage("com.acb.acb")!!
        val body = "So tien: 500,000 VND\nTai khoan: 1012345678\nNoi dung: Test\nACB"
        val tx = acbParser.parse("ACB", body)
        assertThat(tx).isNotNull()
        assertThat(tx!!.bankCode).isEqualTo("ACB")
    }
}
