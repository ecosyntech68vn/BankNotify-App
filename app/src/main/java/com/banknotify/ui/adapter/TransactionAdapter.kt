package com.banknotify.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.banknotify.R
import com.banknotify.core.model.Transaction
import com.banknotify.core.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onClick: (Transaction) -> Unit
) : PagingDataAdapter<Transaction, TransactionAdapter.VH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false))
    }

    override fun onBindViewHolder(holder: VH, pos: Int) = holder.bind(getItem(pos))

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val res = itemView.resources
        private val bankName = itemView.findViewById<TextView>(R.id.item_bank_name)!!
        private val amount = itemView.findViewById<TextView>(R.id.item_amount)!!
        private val content = itemView.findViewById<TextView>(R.id.item_content)!!
        private val time = itemView.findViewById<TextView>(R.id.item_time)!!
        private val badge = itemView.findViewById<TextView>(R.id.item_status)!!
        private val sender = itemView.findViewById<TextView>(R.id.item_sender)!!
        private val card = itemView.findViewById<CardView>(R.id.item_card)!!
        private val df = SimpleDateFormat(res.getString(R.string.item_date_format), Locale.getDefault())

        fun bind(tx: Transaction?) {
            if (tx == null) return
            bankName.text = tx.bankName
            amount.text = res.getString(R.string.item_amount_format, tx.amount)
            amount.setTextColor(if (tx.amount > 0) itemView.context.getColor(R.color.success) else itemView.context.getColor(R.color.error))
            content.text = tx.content
            time.text = df.format(Date(tx.transactionDate))
            sender.text = tx.senderName ?: ""
            badge.text = when (tx.status) {
                TransactionStatus.PENDING -> res.getString(R.string.status_pending)
                TransactionStatus.CONFIRMED -> res.getString(R.string.status_confirmed)
                TransactionStatus.EXPIRED -> res.getString(R.string.status_expired)
                TransactionStatus.FAILED -> res.getString(R.string.status_failed)
            }
            card.setOnClickListener { onClick(tx) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(o: Transaction, n: Transaction) = o.id == n.id
        override fun areContentsTheSame(o: Transaction, n: Transaction) = o == n
    }
}
