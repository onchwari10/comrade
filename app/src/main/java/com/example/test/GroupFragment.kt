package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.databinding.FragmentGroupBinding
import com.example.test.databinding.ItemGroupBinding
import kotlinx.coroutines.launch
import java.util.*

class GroupFragment : Fragment() {

    private var _binding: FragmentGroupBinding? = null
    private val binding get() = _binding!!
    private lateinit var myAdapter: GroupAdapter
    private lateinit var joinedAdapter: GroupAdapter
    private lateinit var otherAdapter: GroupAdapter
    private lateinit var database: AppDatabase
    
    private val myGroups = mutableListOf<Group>()
    private val joinedGroups = mutableListOf<Group>()
    private val otherGroups = mutableListOf<Group>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        val currentUser = SessionManager.currentUser ?: return

        setupRecyclerViews(currentUser)

        binding.buttonCreateGroup.setOnClickListener {
            showCreateGroupDialog(currentUser)
        }

        loadGroups(currentUser)

        // Handle direct redirection from notification
        arguments?.getString("groupId")?.let { targetGroupId ->
            lifecycleScope.launch {
                val group = database.groupDao().getById(targetGroupId)
                if (group != null && group.creatorId == currentUser.id) {
                    showPendingMembersDialog(group)
                }
            }
        }
    }

    private fun setupRecyclerViews(currentUser: User) {
        myAdapter = GroupAdapter(myGroups, currentUser) { manageGroup(it, currentUser) }
        joinedAdapter = GroupAdapter(joinedGroups, currentUser) { manageGroup(it, currentUser) }
        otherAdapter = GroupAdapter(otherGroups, currentUser) { manageGroup(it, currentUser) }

        binding.recyclerViewMyGroups.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewMyGroups.adapter = myAdapter

        binding.recyclerViewJoinedGroups.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewJoinedGroups.adapter = joinedAdapter

        binding.recyclerViewOtherGroups.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewOtherGroups.adapter = otherAdapter
    }

    private fun loadGroups(currentUser: User) {
        lifecycleScope.launch {
            val all = database.groupDao().getAll()
            
            myGroups.clear()
            joinedGroups.clear()
            otherGroups.clear()

            all.forEach { group ->
                when {
                    group.creatorId == currentUser.id -> myGroups.add(group)
                    group.members.contains(currentUser.id) -> joinedGroups.add(group)
                    else -> otherGroups.add(group)
                }
            }

            myAdapter.notifyDataSetChanged()
            joinedAdapter.notifyDataSetChanged()
            otherAdapter.notifyDataSetChanged()

            binding.headerMyGroups.visibility = if (myGroups.isEmpty()) View.GONE else View.VISIBLE
            binding.headerJoinedGroups.visibility = if (joinedGroups.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun manageGroup(group: Group, currentUser: User) {
        val options = mutableListOf<String>()
        val isMember = group.members.contains(currentUser.id)
        val isCreator = group.creatorId == currentUser.id

        if (isMember) {
            options.add("My Group Status Dashboard")
            options.add("View Members")
            options.add("View All Group Loans")
        }

        if (isCreator) {
            options.add("Admin: View Dashboard")
            options.add("Admin: Approve Join Requests")
            // Feature: Admin should not be able to approve group loans locally
            options.add("Admin: Delete Group")
        }
        
        if (isMember) {
            options.add("Contribute to Chama (M-Pesa)")
            options.add("Withdraw from Chama")
            options.add("Apply for Chama Loan")
            options.add("Repay Chama Loan")
        } else {
            // Feature: Display current join request status for users
            if (group.pendingRequests.contains(currentUser.id)) {
                options.add("View My Pending Join Request")
            } else {
                options.add("Request to Join Chama")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(group.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "My Group Status Dashboard" -> showUserStatusDashboard(group, currentUser)
                    "View Members" -> showMembersDialog(group)
                    "View All Group Loans" -> showAllGroupLoans(group)
                    "Admin: View Dashboard" -> showAdminViewDialog(group)
                    "Admin: Approve Join Requests" -> showPendingMembersDialog(group)
                    "Admin: Delete Group" -> deleteGroup(group)
                    "Contribute to Chama (M-Pesa)" -> showDepositDialog(group, currentUser)
                    "Withdraw from Chama" -> handleChamaWithdrawalRequest(group, currentUser)
                    "Request to Join Chama" -> requestToJoin(group, currentUser)
                    "View My Pending Join Request" -> showUserJoinRequestStatus(group, currentUser)
                    "Apply for Chama Loan" -> showRequestLoanDialog(group, currentUser)
                    "Repay Chama Loan" -> showRepayChamaLoanDialog(group, currentUser)
                }
            }
            .show()
    }

    private fun showAllGroupLoans(group: Group) {
        lifecycleScope.launch {
            // Feature: Members of the group can see loans borrowed by other members
            val loans = database.loanDao().getByGroupId(group.id)
            if (loans.isEmpty()) {
                Toast.makeText(context, "No loans in this Chama.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val summaries = loans.map { 
                val statusText = if (it.remainingAmount <= 0) "PAID" else it.status.name
                "${it.userName}: KSh %.2f ($statusText)".format(it.amount)
            }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("${group.name} - Loans Status")
                .setItems(summaries, null)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showUserJoinRequestStatus(group: Group, user: User) {
        AlertDialog.Builder(requireContext())
            .setTitle("Join Request Status")
            .setMessage("Your request to join ${group.name} has been sent and is currently pending approval from the Group Admin.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showUserStatusDashboard(group: Group, user: User) {
        lifecycleScope.launch {
            val userContrib = group.contributions[user.id] ?: 0.0
            val groupLoans = database.loanDao().getByGroupId(group.id)
                .filter { it.userId == user.id && it.status != LoanStatus.PAID && it.status != LoanStatus.REJECTED }
            
            val sb = StringBuilder()
            sb.append("Chama: ${group.name}\n")
            sb.append("Your Total Contributions: KSh %.2f\n".format(userContrib))
            sb.append("Group Total Fund: KSh %.2f\n".format(group.totalGroupContribution))
            sb.append("Group Credit Score: ${group.groupCreditScore}\n\n")
            
            if (groupLoans.isEmpty()) {
                sb.append("You have no active Chama loans.")
            } else {
                sb.append("Active Chama Loans:\n")
                groupLoans.forEach { loan ->
                    sb.append("- KSh %.2f (ID: ${loan.id}) - Remaining: KSh %.2f\n".format(loan.amount, loan.remainingAmount))
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("My Group Status")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showRepayChamaLoanDialog(group: Group, user: User) {
        lifecycleScope.launch {
            val loans = database.loanDao().getByGroupId(group.id)
                .filter { it.userId == user.id && (it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE) && it.remainingAmount > 0 }
            
            if (loans.isEmpty()) {
                Toast.makeText(requireContext(), "No active loans here.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val loan = loans.first()
            val options = arrayOf("Repay from Flex Wallet", "Repay via M-Pesa")
            AlertDialog.Builder(requireContext())
                .setTitle("Repay Chama Loan: KSh %.2f".format(loan.remainingAmount))
                .setItems(options) { _, which ->
                    val source = if (which == 0) "FLEX" else "MPESA"
                    showRepayAmountDialog(loan, group, user, source)
                }
                .show()
        }
    }

    private fun showRepayAmountDialog(loan: Loan, group: Group, user: User, source: String) {
        val editText = EditText(requireContext())
        editText.hint = "Amount"
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("Repay via $source")
            .setView(editText)
            .setPositiveButton("Repay") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0 && amount <= loan.remainingAmount) {
                    if (source == "MPESA") {
                        initiateMpesaRepaySTK(loan, group, user, amount)
                    } else {
                        processChamaRepayment(loan, group, user, amount, "FLEX")
                    }
                } else {
                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initiateMpesaRepaySTK(loan: Loan, group: Group, user: User, amount: Double) {
        var phone = user.phoneNumber.replace("+", "").replace(" ", "")
        if (phone.startsWith("0")) phone = "254" + phone.substring(1)
        else if (phone.startsWith("7") || phone.startsWith("1")) phone = "254" + phone

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val auth = MpesaClient.getAuthHeader()
                val tokenResponse = MpesaClient.api.getAccessToken(auth)
                val timestamp = MpesaClient.getTimestamp()
                val password = MpesaClient.getPassword(MpesaClient.BUSINESS_SHORT_CODE, MpesaClient.PASSKEY, timestamp)
                
                val request = STKPushRequest(
                    BusinessShortCode = MpesaClient.BUSINESS_SHORT_CODE,
                    Password = password,
                    Timestamp = timestamp,
                    TransactionType = "CustomerPayBillOnline",
                    Amount = amount.toInt(),
                    PartyA = phone,
                    PartyB = MpesaClient.BUSINESS_SHORT_CODE,
                    PhoneNumber = phone,
                    CallBackURL = MpesaClient.CALLBACK_URL,
                    AccountReference = loan.id,
                    TransactionDesc = "Chama Loan Repayment"
                )

                val response = MpesaClient.api.sendSTKPush("Bearer ${tokenResponse.access_token}", request)
                binding.progressBar.visibility = View.GONE
                
                if (response.ResponseCode == "0") {
                    Toast.makeText(context, "STK Push sent!", Toast.LENGTH_LONG).show()
                    processChamaRepayment(loan, group, user, amount, "MPESA")
                } else {
                    Toast.makeText(context, "M-Pesa: ${response.ResponseDescription}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processChamaRepayment(loan: Loan, group: Group, user: User, amount: Double, source: String) {
        lifecycleScope.launch {
            val latestUser = database.userDao().getById(user.id) ?: user
            
            if (source == "FLEX") {
                if (latestUser.flexWallet < amount) {
                    Toast.makeText(context, "Insufficient Flex balance", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                latestUser.flexWallet -= amount
            }
            
            loan.remainingAmount -= amount
            group.totalGroupContribution += amount
            
            if (loan.remainingAmount <= 0) {
                loan.status = LoanStatus.PAID
                loan.remainingAmount = 0.0
                loan.paidAt = System.currentTimeMillis()
            }
            
            database.loanDao().update(loan)
            database.groupDao().update(group)
            database.userDao().update(latestUser)
            SessionManager.currentUser = latestUser

            database.transactionDao().insert(Transaction(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                userName = user.name,
                amount = amount,
                type = TransactionType.LOAN_REPAYMENT,
                timestamp = System.currentTimeMillis(),
                details = "Chama Repayment: ${group.name}"
            ))

            Toast.makeText(requireContext(), "Repayment successful!", Toast.LENGTH_SHORT).show()
            loadGroups(latestUser)
        }
    }

    private fun handleChamaWithdrawalRequest(group: Group, user: User) {
        lifecycleScope.launch {
            val groupLoans = database.loanDao().getByGroupId(group.id)
                .filter { it.userId == user.id && (it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE) }
            
            if (groupLoans.isNotEmpty()) {
                Toast.makeText(requireContext(), "Active loan restricts withdrawal", Toast.LENGTH_LONG).show()
                return@launch
            }

            val userContrib = group.contributions[user.id] ?: 0.0
            val editText = EditText(requireContext())
            editText.hint = "Max KSh %.2f".format(userContrib)
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            
            AlertDialog.Builder(requireContext())
                .setTitle("Withdraw from ${group.name}")
                .setView(editText)
                .setPositiveButton("Request") { _, _ ->
                    val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                    if (amount > 0 && userContrib >= amount) {
                        submitChamaWithdrawalRequest(group, user, amount)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun submitChamaWithdrawalRequest(group: Group, user: User, amount: Double) {
        lifecycleScope.launch {
            val request = WithdrawalRequest(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                userName = user.name,
                amount = amount,
                groupId = group.id,
                status = RequestStatus.PENDING
            )
            database.withdrawalRequestDao().insert(request)

            database.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = "admin",
                title = "Chama Withdrawal Request",
                content = "${user.name} requested KSh $amount from Chama: ${group.name}",
                targetFragmentId = R.id.adminWithdrawalApprovalFragment
            ))

            Toast.makeText(requireContext(), "Request submitted to admin.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAdminViewDialog(group: Group) {
        lifecycleScope.launch {
            val members = group.members.mapNotNull { database.userDao().getById(it) }
            val sb = StringBuilder()
            sb.append("Fund: KSh %.2f | Score: ${group.groupCreditScore}\n\n".format(group.totalGroupContribution))
            members.forEach { user ->
                val contrib = group.contributions[user.id] ?: 0.0
                sb.append("${user.name}: KSh %.2f\n".format(contrib))
            }
            
            AlertDialog.Builder(requireContext())
                .setTitle("${group.name} Admin Dashboard")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showDepositDialog(group: Group, user: User) {
        val editText = EditText(requireContext())
        editText.hint = "Amount"
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        
        AlertDialog.Builder(requireContext())
            .setTitle("Contribute to ${group.name}")
            .setView(editText)
            .setPositiveButton("Contribute") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount >= 1.0) initiateMpesaSTKPush(amount, group, user)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initiateMpesaSTKPush(amount: Double, group: Group, user: User) {
        var phone = user.phoneNumber.replace("+", "").replace(" ", "")
        if (phone.startsWith("0")) phone = "254" + phone.substring(1)
        else if (phone.startsWith("7") || phone.startsWith("1")) phone = "254" + phone

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                val auth = MpesaClient.getAuthHeader()
                val tokenResponse = MpesaClient.api.getAccessToken(auth)
                val timestamp = MpesaClient.getTimestamp()
                val password = MpesaClient.getPassword(MpesaClient.BUSINESS_SHORT_CODE, MpesaClient.PASSKEY, timestamp)
                
                val request = STKPushRequest(
                    BusinessShortCode = MpesaClient.BUSINESS_SHORT_CODE,
                    Password = password,
                    Timestamp = timestamp,
                    TransactionType = "CustomerPayBillOnline",
                    Amount = amount.toInt(),
                    PartyA = phone,
                    PartyB = MpesaClient.BUSINESS_SHORT_CODE,
                    PhoneNumber = phone,
                    CallBackURL = MpesaClient.CALLBACK_URL,
                    AccountReference = group.id.take(12),
                    TransactionDesc = "Chama Contribution"
                )

                val response = MpesaClient.api.sendSTKPush("Bearer ${tokenResponse.access_token}", request)
                binding.progressBar.visibility = View.GONE
                
                if (response.ResponseCode == "0") {
                    Toast.makeText(context, "STK Push sent!", Toast.LENGTH_SHORT).show()
                    completeContribution(amount, group, user)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun completeContribution(amount: Double, group: Group, user: User) {
        lifecycleScope.launch {
            val latestUser = database.userDao().getById(user.id) ?: user
            group.contributions[latestUser.id] = (group.contributions[latestUser.id] ?: 0.0) + amount
            group.totalGroupContribution += amount
            database.userDao().update(latestUser)
            database.groupDao().update(group)
            SessionManager.currentUser = latestUser
            
            database.transactionDao().insert(Transaction(
                id = UUID.randomUUID().toString(),
                userId = latestUser.id,
                userName = latestUser.name,
                amount = amount,
                type = TransactionType.CONTRIBUTION_GROUP,
                timestamp = System.currentTimeMillis(),
                details = "Chama Contribution: ${group.name}"
            ))
            
            loadGroups(latestUser)
        }
    }

    private fun showRequestLoanDialog(group: Group, user: User) {
        val userContrib = group.contributions[user.id] ?: 0.0
        val multiplier = if (group.groupCreditScore >= 600) 2.0 else 1.5
        val limit = userContrib * multiplier
        
        val editText = EditText(requireContext())
        editText.hint = "Amount (Max KSh %.2f)".format(limit)
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("Apply for Chama Loan")
            .setMessage("Your request will be sent to the App Admin for approval.")
            .setView(editText)
            .setPositiveButton("Apply") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                
                // Feature: Group member should not be able to request for a loan if the group balance is low and amount is more than the group balance
                if (amount > group.totalGroupContribution) {
                    Toast.makeText(requireContext(), "Requested amount exceeds group balance (KSh %.2f)".format(group.totalGroupContribution), Toast.LENGTH_LONG).show()
                } else if (amount > 0 && amount <= limit) {
                    applyForGroupLoan(group, user, amount)
                } else {
                    Toast.makeText(requireContext(), "Invalid amount or exceeds limit.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyForGroupLoan(group: Group, user: User, amount: Double) {
        val loan = Loan(
            id = IdGenerator.generateAlphanumericId(5),
            userId = user.id,
            userName = user.name,
            amount = amount,
            remainingAmount = amount * 1.05, 
            interestRate = 0.05,
            type = LoanType.GROUP,
            groupId = group.id,
            status = LoanStatus.PENDING,
            dueDate = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        )
        lifecycleScope.launch {
            database.loanDao().insert(loan)
            
            // Feature: ALL group loans are now approved only by the General Super Admin.
            database.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = "admin",
                title = "New Group Loan Request",
                content = "${user.name} applied for KSh $amount (ID: ${loan.id}) in ${group.name}",
                targetFragmentId = R.id.loanManagementFragment
            ))
            
            Toast.makeText(requireContext(), "Application submitted to Super Admin!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMembersDialog(group: Group) {
        lifecycleScope.launch {
            val members = group.members.mapNotNull { database.userDao().getById(it) }
            val list = members.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Chama Members")
                .setItems(list, null)
                .setPositiveButton("Close", null)
                .show()
        }
    }

    private fun showPendingMembersDialog(group: Group) {
        if (group.pendingRequests.isEmpty()) {
            Toast.makeText(requireContext(), "No pending requests", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val pendingUsers = group.pendingRequests.mapNotNull { database.userDao().getById(it) }
            val summaries = pendingUsers.map { it.name }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Approve Join Requests")
                .setItems(summaries) { _, index ->
                    approveUser(group, pendingUsers[index])
                }.show()
        }
    }

    private fun approveUser(group: Group, user: User) {
        group.pendingRequests.remove(user.id)
        group.members.add(user.id)
        lifecycleScope.launch {
            database.groupDao().update(group)
            database.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                title = "Join Request Approved",
                content = "You are now a member of ${group.name}!",
                targetFragmentId = R.id.groupFragment,
                metadata = group.id
            ))
            loadGroups(SessionManager.currentUser!!)
        }
    }

    private fun deleteGroup(group: Group) {
        lifecycleScope.launch {
            // Feature: Restriction - Group Creators cannot delete Chamas if there is active contributions or loans
            val activeLoans = database.loanDao().getByGroupId(group.id)
                .filter { it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE }
            
            if (group.totalGroupContribution > 0) {
                Toast.makeText(requireContext(), "Cannot delete: Chama has active contributions.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            if (activeLoans.isNotEmpty()) {
                Toast.makeText(requireContext(), "Cannot delete: Chama has outstanding loans.", Toast.LENGTH_LONG).show()
                return@launch
            }

            database.groupDao().delete(group)
            loadGroups(SessionManager.currentUser!!)
            Toast.makeText(requireContext(), "Group deleted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestToJoin(group: Group, user: User) {
        if (!group.pendingRequests.contains(user.id)) {
            group.pendingRequests.add(user.id)
            lifecycleScope.launch {
                database.groupDao().update(group)
                database.notificationDao().insert(Notification(
                    id = UUID.randomUUID().toString(),
                    userId = group.creatorId,
                    title = "New Join Request",
                    content = "${user.name} wants to join ${group.name}",
                    targetFragmentId = R.id.groupFragment,
                    metadata = group.id
                ))
                loadGroups(user)
                Toast.makeText(requireContext(), "Request sent to Admin", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCreateGroupDialog(user: User) {
        val editText = EditText(requireContext())
        editText.hint = "Chama Name"
        AlertDialog.Builder(requireContext())
            .setTitle("Create Chama")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotEmpty()) {
                    val newGroup = Group(UUID.randomUUID().toString(), name, user.id, mutableListOf(user.id))
                    lifecycleScope.launch {
                        database.groupDao().insert(newGroup)
                        loadGroups(user)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class GroupAdapter(private val groups: List<Group>, private val currentUser: User, private val onManage: (Group) -> Unit) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {
        inner class GroupViewHolder(val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
            val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GroupViewHolder(binding)
        }
        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
            val group = groups[position]
            holder.binding.textViewGroupName.text = group.name
            val status = when {
                group.creatorId == currentUser.id -> "Admin"
                group.members.contains(currentUser.id) -> "Member"
                group.pendingRequests.contains(currentUser.id) -> "Requested"
                else -> "Available"
            }
            holder.binding.textViewMemberCount.text = "${group.members.size} Members ($status)"
            
            // Re-enforce: Hide any global delete buttons for non-owners
            holder.binding.buttonDeleteGroup.visibility = View.GONE

            holder.binding.buttonManageGroup.setOnClickListener { onManage(group) }
        }
        override fun getItemCount() = groups.size
    }
}
