package com.upialert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.upialert.data.TransactionEntity
import com.upialert.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionsAdapter(private val onDeleteClick: (TransactionEntity) -> Unit) : ListAdapter<TransactionEntity, TransactionsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDeleteClick)
    }

    class ViewHolder(private val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TransactionEntity, onDeleteClick: (TransactionEntity) -> Unit) {
            // Simplified logic: Just showing package name for now
            // In real app, map package name to App Name (e.g., com.phonepe.app -> PhonePe)
            // Priority: Sender Name -> App Name -> "UPI App"
            val appName = if (item.senderName.isNotEmpty() && item.senderName != "Unknown") {
                item.senderName
            } else {
                when {
                    item.appPackage.contains("google", true) -> "Google Pay"
                    item.appPackage.contains("phonepe", true) -> "PhonePe"
                    item.appPackage.contains("paytm", true) -> "Paytm"
                    item.appPackage.contains("bhim", true) -> "BHIM"
                    else -> "UPI App"
                }
            }
            
            binding.tvAppName.text = appName
            binding.tvAmount.text = String.format("₹%.2f", item.amount)
            
            val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            binding.tvTimestamp.text = dateFormat.format(Date(item.timestamp))

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
            return oldItem == newItem
        }
    }
}
