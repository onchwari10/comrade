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
import com.example.test.databinding.FragmentAdminWithdrawalApprovalBinding
import com.example.test.databinding.ItemWithdrawalRequestBinding
import kotlinx.coroutines.launch
import java.util.*

class AdminWithdrawalApprovalFragment : Fragment() {

    private var _binding: FragmentAdminWithdrawalApprovalBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var adapter: RequestAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminWithdrawalApprovalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        adapter = RequestAdapter(mutableListOf(), 
            onApprove = { request -> approveRequest(request) },
            onReject = { request -> rejectRequest(request) }
        )
        binding.recyclerViewRequests.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewRequests.adapter = adapter

        loadRequests()
    }

    private fun loadRequests() {
        lifecycleScope.launch {
            val requests = db.withdrawalRequestDao().getAll().filter { it.status == RequestStatus.PENDING }
            adapter.updateRequests(requests)
        }
    }

    private fun approveRequest(request: WithdrawalRequest) {
        lifecycleScope.launch {
            val user = db.userDao().getById(request.userId) ?: return@launch
            
            if (request.groupId != null) {
                // Chama Withdrawal Approval
                val group = db.groupDao().getById(request.groupId) ?: return@launch
                
                // Final check: ensure no active group loans
                val groupLoans = db.loanDao().getByGroupId(group.id)
                    .filter { it.userId == user.id && (it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE) }
                
                if (groupLoans.isNotEmpty()) {
                    Toast.makeText(context, "Cannot approve: User has active Chama loan", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Process Chama Withdrawal
                val currentContrib = group.contributions[user.id] ?: 0.0
                if (currentContrib < request.amount) {
                    Toast.makeText(context, "Insufficient group savings", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                group.contributions[user.id] = currentContrib - request.amount
                group.totalGroupContribution -= request.amount
                db.groupDao().update(group)

                // Add to user's flex wallet (simulating M-Pesa transfer successful)
                user.flexWallet += request.amount
                db.userDao().update(user)

                db.transactionDao().insert(Transaction(
                    id = UUID.randomUUID().toString(),
                    userId = user.id,
                    userName = user.name,
                    amount = request.amount,
                    type = TransactionType.WITHDRAWAL_FLEX,
                    timestamp = System.currentTimeMillis(),
                    details = "Chama Withdrawal Approved: ${group.name}"
                ))

                request.status = RequestStatus.APPROVED
                request.approvedAt = System.currentTimeMillis()
                request.releaseDate = System.currentTimeMillis() // Immediate for Chama
                db.withdrawalRequestDao().update(request)

                // Notify User
                db.notificationDao().insert(Notification(
                    id = UUID.randomUUID().toString(),
                    userId = user.id,
                    title = "Chama Withdrawal Approved",
                    content = "Your request for KSh ${request.amount} from ${group.name} was approved and disbursed.",
                    targetFragmentId = R.id.savingsFragment
                ))

                Toast.makeText(context, "Chama Withdrawal Approved", Toast.LENGTH_SHORT).show()
            } else {
                // Individual Locked Savings Approval
                request.status = RequestStatus.APPROVED
                request.approvedAt = System.currentTimeMillis()
                
                // Release date set to 1 month from now
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, 1)
                request.releaseDate = calendar.timeInMillis
                
                db.withdrawalRequestDao().update(request)
                
                // Notify User
                db.notificationDao().insert(Notification(
                    id = UUID.randomUUID().toString(),
                    userId = request.userId,
                    title = "Withdrawal Approved",
                    content = "Your request for KSh ${request.amount} has been approved. Funds will be released on ${java.text.DateFormat.getDateInstance().format(Date(request.releaseDate!!))}.",
                    targetFragmentId = R.id.savingsFragment
                ))

                Toast.makeText(context, "Individual Request Approved. Release in 1 month.", Toast.LENGTH_SHORT).show()
            }

            loadRequests()
        }
    }

    private fun rejectRequest(request: WithdrawalRequest) {
        lifecycleScope.launch {
            request.status = RequestStatus.REJECTED
            db.withdrawalRequestDao().update(request)

            // Notify User
            db.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = request.userId,
                title = "Withdrawal Rejected",
                content = "Your request for KSh ${request.amount} was rejected.",
                targetFragmentId = R.id.savingsFragment
            ))

            loadRequests()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class RequestAdapter(
        private var requests: List<WithdrawalRequest>,
        private val onApprove: (WithdrawalRequest) -> Unit,
        private val onReject: (WithdrawalRequest) -> Unit
    ) : RecyclerView.Adapter<RequestAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemWithdrawalRequestBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemWithdrawalRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val request = requests[position]
            val type = if (request.groupId != null) "Chama" else "Locked"
            holder.binding.textViewUser.text = "${request.userName} ($type)"
            holder.binding.textViewAmount.text = "KSh %.2f".format(request.amount)
            holder.binding.textViewStatus.text = "Status: ${request.status}"

            if (request.status == RequestStatus.PENDING) {
                holder.binding.layoutActions.visibility = View.VISIBLE
            } else {
                holder.binding.layoutActions.visibility = View.GONE
            }

            holder.binding.buttonApprove.setOnClickListener { onApprove(request) }
            holder.binding.buttonReject.setOnClickListener { onReject(request) }
        }

        override fun getItemCount() = requests.size

        fun updateRequests(newRequests: List<WithdrawalRequest>) {
            requests = newRequests
            notifyDataSetChanged()
        }
    }
}
