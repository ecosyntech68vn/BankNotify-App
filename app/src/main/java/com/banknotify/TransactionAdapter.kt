package com.banknotify

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.banknotify.model.Transaction
import com.banknotify.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(
    private val onClick: (Transaction) -> Unit
) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val bankName: TextView = itemView.findViewById(R.id.item_bank_name)
        private val amount: TextView = itemView.findViewById(R.id.item_amount)
        private val content: TextView = itemView.findViewById(R.id.item_content)
        private val time: TextView = itemView.findViewById(R.id.item_time)
        private val statusBadge: TextView = itemView.findViewById(R.id.item_status)
        private val sender: TextView = itemView.findViewById(R.id.item_sender)
        private val card: CardView = itemView.findViewById(R.id.item_card)

        fun bind(tx: Transaction) {
            bankName.text = tx.bankName
            amount.text = "+${String.format("%,.0f", tx.amount)} VND"
            amount.setTextColor(
                if (tx.amount > 0) itemView.context.getColor(android.R.color.holo_green_dark)
                else itemView.context.getColor(android.R.color.holo_red_dark)
            )
            content.text = tx.content
            time.text = dateFormat.format(Date(tx.transactionDate))
            sender.text = tx.senderName ?: ""

            statusBadge.text = when (tx.status) {
                TransactionStatus.PENDING -> "Mới"
                TransactionStatus.CONFIRMED -> "Đã XN"
                TransactionStatus.EXPIRED -> "Hết hạn"
                TransactionStatus.FAILED -> "Lỗi"
            }
            statusBadge.setTextColor(
                itemView.context.getColor(
                    when (tx.status) {
                        TransactionStatus.PENDING -> android.R.color.holo_orange_dark
                        TransactionStatus.CONFIRMED -> android.R.color.holo_green_dark
                        TransactionStatus.EXPIRED -> android.R.color.darker_gray
                        TransactionStatus.FAILED -> android.R.color.holo_red_dark
                    }
                )
            )

            card.setOnClickListener { onClick(tx) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(old: Transaction, new: Transaction) = old.id == new.id
        override fun areContentsTheSame(old: Transaction, new: Transaction) = old == new
    }
}
