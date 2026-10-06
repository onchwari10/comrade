package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.databinding.FragmentLoanManagementBinding
import com.example.test.databinding.ItemLoanBinding
import kotlinx.coroutines.launch
import java.util.*

class LoanManagementFragment : Fragment() {

    private var _binding: FragmentLoanManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var adapter: LoanAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoanManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        
        adapter = LoanAdapter(mutableListOf(), 
            onApprove = { loan -> approveLoan(loan) },
            onReject = { loan -> rejectLoan(loan) }
        )
        
        binding.recyclerViewLoans.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewLoans.adapter = adapter
        
        loadLoans()
    }

    private fun loadLoans() {
        lifecycleScope.launch {
            val allLoans = db.loanDao().getAll()
            adapter.updateLoans(allLoans)
        }
    }

    private fun approveLoan(loan: Loan) {
        lifecycleScope.launch {
            val user = db.userDao().getById(loan.userId) ?: return@launch
            
            // Feature: General Super Admin approves ALL loans (Individual and Group)
            if (loan.type == LoanType.GROUP) {
                val group = db.groupDao().getById(loan.groupId ?: "")
                if (group != null) {
                    if (group.totalGroupContribution < loan.amount) {
                        Toast.makeText(context, "Insufficient Chama funds to disburse.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    // Deduct from group funds
                    group.totalGroupContribution -= loan.amount
                    db.groupDao().update(group)
                }
            }

            loan.status = LoanStatus.APPROVED
            loan.remainingAmount = loan.amount
            db.loanDao().update(loan)
            
            // Disburse to user's flex wallet
            user.flexWallet += loan.amount
            db.userDao().update(user)
            
            db.transactionDao().insert(Transaction(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                userName = user.name,
                amount = loan.amount,
                type = TransactionType.LOAN_DISBURSEMENT,
                timestamp = System.currentTimeMillis(),
                details = "Loan Approved (ID: ${loan.id})"
            ))

            // Notify User
            db.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                title = "Loan Approved",
                content = "Your loan of KSh ${loan.amount} (ID: ${loan.id}) has been approved and disbursed.",
                targetFragmentId = R.id.loansFragment
            ))
            
            Toast.makeText(context, "Loan for ${loan.userName} approved!", Toast.LENGTH_SHORT).show()
            loadLoans()
        }
    }

    private fun rejectLoan(loan: Loan) {
        lifecycleScope.launch {
            loan.status = LoanStatus.REJECTED
            db.loanDao().update(loan)

            // Notify User
            db.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = loan.userId,
                title = "Loan Rejected",
                content = "Unfortunately, your loan application for KSh ${loan.amount} was rejected.",
                targetFragmentId = R.id.loansFragment
            ))

            loadLoans()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LoanAdapter(
        private var loans: List<Loan>,
        private val onApprove: (Loan) -> Unit,
        private val onReject: (Loan) -> Unit
    ) : RecyclerView.Adapter<LoanAdapter.LoanViewHolder>() {

        inner class LoanViewHolder(val binding: ItemLoanBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoanViewHolder {
            val binding = ItemLoanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return LoanViewHolder(binding)
        }

        override fun onBindViewHolder(holder: LoanViewHolder, position: Int) {
            val loan = loans[position]
            val statusText = if (loan.remainingAmount <= 0 && (loan.status == LoanStatus.PAID || loan.status == LoanStatus.APPROVED)) "PAID" else loan.status.name
            
            holder.binding.textViewApplicant.text = "${loan.userName} ($statusText) - ${loan.type.name}"
            holder.binding.textViewAmount.text = "Principal: KSh %.2f | Owed: KSh %.2f | ID: ${loan.id}".format(loan.amount, loan.remainingAmount)
            
            if (loan.status == LoanStatus.PENDING) {
                holder.binding.buttonApprove.visibility = View.VISIBLE
                holder.binding.buttonReject.visibility = View.VISIBLE
                holder.binding.buttonApprove.alpha = 1.0f
            } else {
                holder.binding.buttonApprove.visibility = View.GONE
                holder.binding.buttonReject.visibility = View.GONE
            }
            
            holder.binding.buttonApprove.setOnClickListener { onApprove(loan) }
            holder.binding.buttonReject.setOnClickListener { onReject(loan) }

            if (statusText == "PAID") {
                holder.binding.textViewApplicant.setTextColor(android.graphics.Color.BLUE)
            } else if (loan.status == LoanStatus.REJECTED) {
                holder.binding.textViewApplicant.setTextColor(android.graphics.Color.RED)
            } else {
                holder.binding.textViewApplicant.setTextColor(android.graphics.Color.BLACK)
            }
        }

        override fun getItemCount() = loans.size

        fun updateLoans(newLoans: List<Loan>) {
            loans = newLoans
            notifyDataSetChanged()
        }
    }
}
