package com.example.test

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TransactionAdapter(private var transactions: List<Transaction>) :
    RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textViewTransactionTitle)
        val amount: TextView = view.findViewById(R.id.textViewTransactionAmount)
        val date: TextView = view.findViewById(R.id.textViewTransactionDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactions[position]
        
        val typeStr = when (transaction.type) {
            TransactionType.DEPOSIT_LOCKED -> "Locked Deposit"
            TransactionType.DEPOSIT_FLEX -> "Flex Deposit"
            TransactionType.WITHDRAWAL_FLEX -> "Flex Withdrawal"
            TransactionType.WITHDRAWAL_LOCKED -> "Locked Withdrawal"
            TransactionType.LOAN_DISBURSEMENT -> "Loan Received"
            TransactionType.LOAN_REPAYMENT -> "Loan Repayment"
            TransactionType.CONTRIBUTION_GROUP -> "Group Contribution"
            TransactionType.TRANSFER_LOCKED_TO_FLEX -> "Internal Transfer"
            else -> "Transaction"
        }
        
        holder.title.text = "${transaction.details.ifEmpty { typeStr }}"
        holder.date.text = dateFormat.format(Date(transaction.timestamp))
        
        val isExpense = transaction.type == TransactionType.WITHDRAWAL_FLEX || 
                       transaction.type == TransactionType.WITHDRAWAL_LOCKED ||
                       transaction.type == TransactionType.CONTRIBUTION_GROUP ||
                       (transaction.type == TransactionType.TRANSFER_LOCKED_TO_FLEX && transaction.details.contains("Out", true))
                       
        // Repayment is also an outflow of cash from a wallet
        val isOutflow = isExpense || transaction.type == TransactionType.LOAN_REPAYMENT
                       
        if (isOutflow) {
            holder.amount.text = "-KSh %.2f".format(transaction.amount)
            holder.amount.setTextColor(android.graphics.Color.RED)
        } else {
            holder.amount.text = "+KSh %.2f".format(transaction.amount)
            holder.amount.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // Green
        }
    }

    override fun getItemCount() = transactions.size

    fun updateData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}
