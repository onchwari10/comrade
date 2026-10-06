package com.example.test

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.databinding.FragmentLoansBinding
import com.example.test.databinding.ItemLoanHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LoansFragment : Fragment() {

    private var _binding: FragmentLoansBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var historyAdapter: LoanHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoansBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        
        historyAdapter = LoanHistoryAdapter(mutableListOf())
        binding.recyclerViewLoanHistory.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewLoanHistory.adapter = historyAdapter

        refreshUI()

        binding.buttonApplyLoan.setOnClickListener {
            showApplyLoanDialog()
        }

        binding.buttonRepayLoan.setOnClickListener {
            showRepayDialog()
        }
    }

    private fun refreshUI() {
        val user = SessionManager.currentUser ?: return
        
        lifecycleScope.launch {
            val updatedUser = db.userDao().getById(user.id) ?: user
            SessionManager.currentUser = updatedUser
            
            val limit = updatedUser.maxBorrowingPower
            binding.textViewLimitAmount.text = "KSh %.2f".format(limit)

            val loans = db.loanDao().getByUserId(user.id).sortedByDescending { it.createdAt }
            historyAdapter.updateLoans(loans)

            // Feature: Active loan is one that is APPROVED/OVERDUE and has remaining balance > 0
            val activeLoans = loans.filter { (it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE) && it.remainingAmount > 0 }
            val pendingLoan = loans.find { it.status == LoanStatus.PENDING }

            if (activeLoans.isNotEmpty()) {
                activeLoans.forEach { loan ->
                    if (System.currentTimeMillis() > loan.dueDate && loan.status != LoanStatus.OVERDUE) {
                        loan.status = LoanStatus.OVERDUE
                        db.loanDao().update(loan)
                    }
                }

                binding.cardActiveLoan.visibility = View.VISIBLE
                binding.textViewNoActiveLoan.visibility = View.GONE
                
                if (activeLoans.size == 1) {
                    val loan = activeLoans[0]
                    binding.textViewLoanAmount.text = "${loan.type} Principal: KSh %.2f (ID: ${loan.id})".format(loan.amount)
                    binding.textViewRemainingAmount.text = "Remaining: KSh %.2f".format(loan.remainingAmount)
                } else {
                    val totalRemaining = activeLoans.sumOf { it.remainingAmount }
                    binding.textViewLoanAmount.text = "Multiple Active Loans (${activeLoans.size})"
                    binding.textViewRemainingAmount.text = "Total Remaining: KSh %.2f".format(totalRemaining)
                }
                
                binding.layoutApplyLoan.visibility = View.GONE 
                
            } else if (pendingLoan != null) {
                binding.cardActiveLoan.visibility = View.GONE
                binding.textViewNoActiveLoan.visibility = View.VISIBLE
                binding.textViewNoActiveLoan.text = "You have a PENDING loan application"
                
                binding.layoutApplyLoan.visibility = View.GONE 
            } else {
                binding.cardActiveLoan.visibility = View.GONE
                binding.textViewNoActiveLoan.visibility = View.VISIBLE
                binding.textViewNoActiveLoan.text = "You have no active loans."
                
                binding.layoutApplyLoan.visibility = View.VISIBLE 
                binding.buttonApplyLoan.isEnabled = limit >= 100
            }
        }
    }

    private fun showApplyLoanDialog() {
        val user = SessionManager.currentUser ?: return
        val limit = user.maxBorrowingPower

        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val editTextAmount = EditText(context).apply {
            hint = "Amount (Max: KSh %.2f)".format(limit)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val textViewPeriod = TextView(context).apply {
            text = "Select Repayment Period:"
            setPadding(0, 20, 0, 10)
        }

        val spinnerPeriod = Spinner(context)
        val periods = arrayOf("1 Week", "2 Weeks", "3 Weeks", "1 Month")
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, periods)
        spinnerPeriod.adapter = adapter

        layout.addView(editTextAmount)
        layout.addView(textViewPeriod)
        layout.addView(spinnerPeriod)

        AlertDialog.Builder(context)
            .setTitle("Request Instant Loan")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val amount = editTextAmount.text.toString().toDoubleOrNull() ?: 0.0
                val selectedPeriod = spinnerPeriod.selectedItemPosition
                if (amount >= 100 && amount <= limit) {
                    processInstantLoan(user, amount, selectedPeriod)
                } else {
                    Toast.makeText(context, "Invalid amount (Min KSh 100, Max KSh %.2f)".format(limit), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processInstantLoan(user: User, amount: Double, periodIndex: Int) {
        val interestRate = if (user.creditScore >= 700) 0.05 else if (user.creditScore >= 500) 0.08 else 0.10
        val totalToRepay = amount * (1 + interestRate)
        
        val durationMillis = when (periodIndex) {
            0 -> 7L * 24 * 60 * 60 * 1000 // 1 Week
            1 -> 14L * 24 * 60 * 60 * 1000 // 2 Weeks
            2 -> 21L * 24 * 60 * 60 * 1000 // 3 Weeks
            else -> 30L * 24 * 60 * 60 * 1000 // 1 Month
        }

        val loan = Loan(
            id = IdGenerator.generateAlphanumericId(5),
            userId = user.id,
            userName = user.name,
            amount = amount,
            remainingAmount = totalToRepay,
            interestRate = interestRate,
            status = LoanStatus.APPROVED,
            createdAt = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + durationMillis
        )

        lifecycleScope.launch {
            db.loanDao().insert(loan)
            
            val latestUser = db.userDao().getById(user.id) ?: user
            latestUser.flexWallet += amount
            db.userDao().update(latestUser)
            SessionManager.currentUser = latestUser
            
            db.transactionDao().insert(Transaction(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                userName = user.name,
                amount = amount,
                type = TransactionType.LOAN_DISBURSEMENT,
                timestamp = System.currentTimeMillis(),
                details = "Instant Loan ID: ${loan.id}"
            ))

            Toast.makeText(context, "Loan of KSh %.2f disbursed to Flex Wallet!".format(amount), Toast.LENGTH_LONG).show()
            refreshUI()
        }
    }

    private fun showRepayDialog() {
        val user = SessionManager.currentUser ?: return
        lifecycleScope.launch {
            val activeLoans = db.loanDao().getByUserId(user.id).filter { 
                (it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE) && it.remainingAmount > 0 
            }
            
            if (activeLoans.isEmpty()) {
                Toast.makeText(context, "No active loans to repay", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (activeLoans.size == 1) {
                showRepaymentSourceOptions(activeLoans[0])
            } else {
                val loanDescriptions = activeLoans.map { "${it.type} Loan: KSh ${it.remainingAmount} (ID: ${it.id})" }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle("Select Loan to Repay")
                    .setItems(loanDescriptions) { _, which ->
                        showRepaymentSourceOptions(activeLoans[which])
                    }
                    .show()
            }
        }
    }

    private fun showRepaymentSourceOptions(loan: Loan) {
        val options = arrayOf("Repay from Flex Wallet", "Repay via M-Pesa")
        AlertDialog.Builder(requireContext())
            .setTitle("Repay ${loan.type} Loan: KSh %.2f".format(loan.remainingAmount))
            .setItems(options) { _, which ->
                val source = when(which) {
                    0 -> "FLEX"
                    else -> "MPESA"
                }
                showRepayAmountDialog(loan, source)
            }
            .show()
    }

    private fun showRepayAmountDialog(loan: Loan, source: String) {
        val editText = EditText(requireContext())
        editText.hint = "Enter amount"
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("Repay from $source")
            .setView(editText)
            .setPositiveButton("Repay") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0 && amount <= loan.remainingAmount) {
                    if (source == "MPESA") {
                        initiateMpesaRepayment(loan, amount)
                    } else {
                        processRepayment(loan, amount, source)
                    }
                } else {
                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initiateMpesaRepayment(loan: Loan, amount: Double) {
        val user = SessionManager.currentUser ?: return
        
        var phone = user.phoneNumber.replace("+", "").replace(" ", "")
        if (phone.startsWith("0")) {
            phone = "254" + phone.substring(1)
        } else if (phone.startsWith("7") || phone.startsWith("1")) {
            phone = "254" + phone
        }

        if (phone.length != 12) {
            Toast.makeText(context, "Invalid phone number for M-Pesa: $phone", Toast.LENGTH_LONG).show()
            return
        }

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
                    TransactionDesc = "Loan Repayment"
                )

                val response = MpesaClient.api.sendSTKPush("Bearer ${tokenResponse.access_token}", request)
                
                binding.progressBar.visibility = View.GONE
                
                if (response.ResponseCode == "0") {
                    Toast.makeText(context, "STK Push sent! Enter PIN on your phone.", Toast.LENGTH_LONG).show()
                    processRepayment(loan, amount, "MPESA")
                } else {
                    Toast.makeText(context, "M-Pesa: ${response.ResponseDescription}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                val appLine = e.stackTrace.find { it.className.startsWith("com.example.test") }
                val errorLine = appLine?.toString() ?: "Unknown line"
                Log.e("LoansFragment", "Error at $errorLine", e)
                Toast.makeText(context, "Error: ${e.message} at $errorLine", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processRepayment(loan: Loan, amount: Double, source: String) {
        val user = SessionManager.currentUser ?: return
        
        lifecycleScope.launch {
            val latestUser = db.userDao().getById(user.id) ?: user
            
            if (source == "FLEX") {
                if (latestUser.flexWallet < amount) {
                    Toast.makeText(context, "Insufficient funds in Flex Wallet", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                latestUser.flexWallet -= amount
            }
            
            loan.remainingAmount -= amount
            
            if (loan.remainingAmount <= 0) {
                loan.status = LoanStatus.PAID
                loan.remainingAmount = 0.0
                loan.paidAt = System.currentTimeMillis() // Track when it was paid for scoring
            }
            
            db.loanDao().update(loan)

            // Update Group funds if it's a group loan
            if (loan.type == LoanType.GROUP && loan.groupId != null) {
                val group = db.groupDao().getById(loan.groupId)
                if (group != null) {
                    group.totalGroupContribution += amount
                    db.groupDao().update(group)
                }
            }

            db.userDao().update(latestUser)
            SessionManager.currentUser = latestUser

            db.transactionDao().insert(Transaction(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                userName = user.name,
                amount = amount,
                type = TransactionType.LOAN_REPAYMENT,
                timestamp = System.currentTimeMillis(),
                details = "Repayment from $source (Loan ID: ${loan.id})"
            ))

            Toast.makeText(context, "Repayment successful!", Toast.LENGTH_SHORT).show()
            refreshUI()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class LoanHistoryAdapter(private var loans: List<Loan>) :
        RecyclerView.Adapter<LoanHistoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemLoanHistoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLoanHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val loan = loans[position]
            holder.binding.textViewLoanAmount.text = "KSh %.2f (ID: ${loan.id})".format(loan.amount)
            holder.binding.textViewLoanType.text = "${loan.type.name} (%.0f%% Int)".format(loan.interestRate * 100)
            
            val statusText = if (loan.remainingAmount <= 0 && loan.status == LoanStatus.PAID) "PAID" else loan.status.name
            holder.binding.textViewLoanStatus.text = statusText
            
            val color = when(statusText) {
                "PENDING" -> android.graphics.Color.BLUE
                "APPROVED" -> android.graphics.Color.GREEN
                "REJECTED" -> android.graphics.Color.RED
                "PAID" -> android.graphics.Color.GRAY
                "OVERDUE" -> android.graphics.Color.RED
                else -> android.graphics.Color.BLACK
            }
            holder.binding.textViewLoanStatus.setTextColor(color)
        }

        override fun getItemCount() = loans.size

        fun updateLoans(newLoans: List<Loan>) {
            loans = newLoans
            notifyDataSetChanged()
        }
    }
}
