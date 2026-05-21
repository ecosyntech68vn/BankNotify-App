package com.banknotify.core.db

import androidx.test.core.app.ApplicationProvider
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionFilter
import com.banknotify.core.model.TransactionStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DatabaseHelperTest {

    private lateinit var db: DatabaseHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appDb = AppDatabase.getInstance(context)
        appDb.transactionDao().deleteAll()
        db = DatabaseHelper(context)
    }

    private fun createTx(
        bankCode: String = "VCB",
        bankName: String = "Vietcombank",
        accountNumber: String = "1012345678",
        amount: Double = 500000.0,
        balance: Double? = 5000000.0,
        content: String = "Chuyen tien",
        senderName: String? = "Nguyen Van A",
        referenceNumber: String? = "REF123",
        status: TransactionStatus = TransactionStatus.PENDING
    ) = Transaction(
        bankCode = bankCode,
        bankName = bankName,
        accountNumber = accountNumber,
        amount = amount,
        balance = balance,
        content = content,
        senderName = senderName,
        referenceNumber = referenceNumber,
        rawMessage = "raw $content",
        status = status
    )

    @Test
    fun `insert and retrieve transaction`() {
        val tx = createTx()
        val id = db.insertTransaction(tx)
        assertThat(id).isGreaterThan(0)

        val saved = db.getTransaction(id)
        assertThat(saved).isNotNull()
        assertThat(saved!!.bankCode).isEqualTo("VCB")
        assertThat(saved.amount).isEqualTo(500000.0)
        assertThat(saved.balance).isEqualTo(5000000.0)
        assertThat(saved.content).isEqualTo("Chuyen tien")
        assertThat(saved.senderName).isEqualTo("Nguyen Van A")
        assertThat(saved.referenceNumber).isEqualTo("REF123")
        assertThat(saved.status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `insert multiple transactions and list them`() {
        db.insertTransaction(createTx(amount = 100000.0, content = "First"))
        db.insertTransaction(createTx(amount = 200000.0, content = "Second"))
        db.insertTransaction(createTx(amount = 300000.0, content = "Third"))

        val all = db.getRecentTransactions(limit = 10)
        assertThat(all).hasSize(3)
    }

    @Test
    fun `filter by bank code`() {
        db.insertTransaction(createTx(bankCode = "VCB"))
        db.insertTransaction(createTx(bankCode = "TCB", bankName = "Techcombank"))
        db.insertTransaction(createTx(bankCode = "MB", bankName = "MB Bank"))

        val vcbTxs = db.getTransactions(TransactionFilter(bankCode = "VCB"))
        assertThat(vcbTxs).hasSize(1)
        assertThat(vcbTxs[0].bankCode).isEqualTo("VCB")
    }

    @Test
    fun `filter by status`() {
        db.insertTransaction(createTx(status = TransactionStatus.PENDING))
        db.insertTransaction(createTx(status = TransactionStatus.CONFIRMED))

        val pending = db.getTransactions(TransactionFilter(status = TransactionStatus.PENDING))
        assertThat(pending).hasSize(1)
        assertThat(pending[0].status).isEqualTo(TransactionStatus.PENDING)
    }

    @Test
    fun `filter by amount range`() {
        db.insertTransaction(createTx(amount = 50000.0))
        db.insertTransaction(createTx(amount = 150000.0))
        db.insertTransaction(createTx(amount = 300000.0))

        val result = db.getTransactions(TransactionFilter(minAmount = 100000.0, maxAmount = 200000.0))
        assertThat(result).hasSize(1)
        assertThat(result[0].amount).isEqualTo(150000.0)
    }

    @Test
    fun `filter by date range`() {
        val now = System.currentTimeMillis()
        db.insertTransaction(createTx(transactionDate = now - 60000, content = "old"))
        db.insertTransaction(createTx(transactionDate = now, content = "current"))

        val result = db.getTransactions(TransactionFilter(fromDate = now - 30000))
        assertThat(result).hasSize(1)
        assertThat(result[0].content).isEqualTo("current")
    }

    @Test
    fun `search by content`() {
        db.insertTransaction(createTx(content = "Chuyen tien mua hang"))
        db.insertTransaction(createTx(content = "Thanh toan hoa don"))

        val result = db.getTransactions(TransactionFilter(searchContent = "mua hang"))
        assertThat(result).hasSize(1)
    }

    @Test
    fun `update transaction status`() {
        val id = db.insertTransaction(createTx())
        val updated = db.updateStatus(id, TransactionStatus.CONFIRMED)
        assertThat(updated).isEqualTo(1)

        val tx = db.getTransaction(id)
        assertThat(tx!!.status).isEqualTo(TransactionStatus.CONFIRMED)
    }

    @Test
    fun `get transaction by reference`() {
        db.insertTransaction(createTx(referenceNumber = "UNIQUE123"))
        val tx = db.getTransactionByReference("UNIQUE123")
        assertThat(tx).isNotNull()
        assertThat(tx!!.referenceNumber).isEqualTo("UNIQUE123")
    }

    @Test
    fun `get unread count`() {
        db.insertTransaction(createTx(status = TransactionStatus.PENDING))
        db.insertTransaction(createTx(status = TransactionStatus.PENDING))
        db.insertTransaction(createTx(status = TransactionStatus.CONFIRMED))

        assertThat(db.getUnreadCount()).isEqualTo(2)
    }

    @Test
    fun `get total statistics`() {
        db.insertTransaction(createTx(amount = 100000.0))
        db.insertTransaction(createTx(amount = 200000.0))

        assertThat(db.getTotalTransactions()).isEqualTo(2)
        assertThat(db.getTotalAmount()).isEqualTo(300000.0)
    }

    @Test
    fun `delete transaction`() {
        val id = db.insertTransaction(createTx())
        assertThat(db.getTransaction(id)).isNotNull()

        db.deleteTransaction(id)
        assertThat(db.getTransaction(id)).isNull()
    }

    @Test
    fun `delete all transactions`() {
        db.insertTransaction(createTx())
        db.insertTransaction(createTx(amount = 999.0))
        assertThat(db.getTotalTransactions()).isEqualTo(2)

        db.deleteAllTransactions()
        assertThat(db.getTotalTransactions()).isEqualTo(0)
    }

    @Test
    fun `insert transaction with null balance`() {
        val tx = createTx(balance = null)
        val id = db.insertTransaction(tx)
        val saved = db.getTransaction(id)
        assertThat(saved!!.balance).isNull()
    }

    @Test
    fun `insert transaction with null sender and reference`() {
        val tx = createTx(senderName = null, referenceNumber = null)
        val id = db.insertTransaction(tx)
        val saved = db.getTransaction(id)
        assertThat(saved!!.senderName).isNull()
        assertThat(saved.referenceNumber).isNull()
    }

    @Test
    fun `get transactions with pagination`() {
        repeat(10) { i ->
            db.insertTransaction(createTx(amount = (i + 1) * 1000.0, content = "tx$i"))
        }

        val page1 = db.getRecentTransactions(limit = 3, offset = 0)
        assertThat(page1).hasSize(3)

        val page2 = db.getRecentTransactions(limit = 3, offset = 3)
        assertThat(page2).hasSize(3)
        assertThat(page2[0].amount).isLessThan(page1[2].amount)
    }
}
