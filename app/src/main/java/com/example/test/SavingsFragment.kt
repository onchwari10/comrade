package com.example.test

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.test.databinding.FragmentSavingsBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SavingsFragment : Fragment() {

    private var _binding: FragmentSavingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        refreshUI()

        binding.buttonDepositLocked.setOnClickListener {
            showLockedDepositDialog()
        }

        binding.buttonDepositFlex.setOnClickListener {
            showDepositDialog(TransactionType.DEPOSIT_FLEX)
        }

        binding.buttonWithdrawFlex.setOnClickListener {
            handleFlexWithdrawal()
        }

        binding.buttonWithdrawLocked.setOnClickListener {
            handleLockedWithdrawal()
        }

        binding.buttonTransfer.visibility = View.GONE
    }

    private fun refreshUI() {
        val user = SessionManager.currentUser ?: return
        lifecycleScope.launch {
            val updatedUser = db.userDao().getById(user.id) ?: user
            SessionManager.currentUser = updatedUser
            
            binding.textViewLockedAmount.text = "KSh %.2f".format(updatedUser.lockedSavings)
            binding.textViewFlexAmount.text = "KSh %.2f".format(updatedUser.flexWallet)
            
            val limitAmount = updatedUser.flexWallet * updatedUser.withdrawalLimitPercent
            binding.textViewFlexLimit.text = "Daily Withdrawal Limit: KSh %.2f (%.0f%%)"
                .format(limitAmount, updatedUser.withdrawalLimitPercent * 100)
            
            binding.textViewCreditScore.text = updatedUser.creditScore.toString()
            binding.textViewMultiplier.text = "%.1fx".format(updatedUser.borrowingMultiplier)
            
            val scoreColor = when {
                updatedUser.creditScore >= 700 -> android.graphics.Color.GREEN
                updatedUser.creditScore >= 500 -> android.graphics.Color.BLUE
                else -> android.graphics.Color.parseColor("#FFA500") 
            }
            binding.textViewCreditScore.setTextColor(scoreColor)
        }
    }

    private fun showLockedDepositDialog() {
        val options = arrayOf("Deposit via M-Pesa STK Push", "Transfer from Flex Wallet")
        AlertDialog.Builder(requireContext())
            .setTitle("Deposit to Locked Savings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDepositDialog(TransactionType.DEPOSIT_LOCKED)
                    1 -> showTransferFromFlexDialog()
                }
            }
            .show()
    }

    private fun showTransferFromFlexDialog() {
        val user = SessionManager.currentUser ?: return
        val editText = EditText(requireContext())
        editText.hint = "Amount (Max: %.2f)".format(user.flexWallet)
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle("Transfer from Flex to Locked")
            .setView(editText)
            .setPositiveButton("Transfer") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount > 0 && amount <= user.flexWallet) {
                    processFlexToLockedTransfer(amount)
                } else {
                    Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processFlexToLockedTransfer(amount: Double) {
        val user = SessionManager.currentUser ?: return
        lifecycleScope.launch {
            val latestUser = db.userDao().getById(user.id) ?: user
            latestUser.flexWallet -= amount
            latestUser.lockedSavings += amount
            
            db.userDao().update(latestUser)
            SessionManager.currentUser = latestUser
            recordTransaction(TransactionType.TRANSFER_FLEX_TO_LOCKED, amount, "Transfer: Flex to Locked Savings")
            refreshUI()
            Toast.makeText(context, "Transfer successful!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDepositDialog(type: TransactionType) {
        val editText = EditText(requireContext())
        val title = if (type == TransactionType.DEPOSIT_LOCKED) "Deposit to Locked Savings" else "Deposit to Flex Wallet"
        editText.hint = "Enter amount"
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage("Deposit securely via M-Pesa STK Push.")
            .setView(editText)
            .setPositiveButton("Deposit") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount >= 1.0) {
                    initiateMpesaSTKPush(amount, type)
                } else {
                    Toast.makeText(context, "Minimum deposit is KSh 1", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun initiateMpesaSTKPush(amount: Double, type: TransactionType) {
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
                    AccountReference = user.id.take(12),
                    TransactionDesc = "Deposit to ${type.name}"
                )

                val response = MpesaClient.api.sendSTKPush("Bearer ${tokenResponse.access_token}", request)
                
                binding.progressBar.visibility = View.GONE
                
                if (response.ResponseCode == "0") {
                    Toast.makeText(context, "STK Push sent! Enter PIN on your phone.", Toast.LENGTH_LONG).show()
                    completeDeposit(amount, type)
                } else {
                    Toast.makeText(context, "M-Pesa: ${response.ResponseDescription}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                val appLine = e.stackTrace.find { it.className.contains("com.example.test") || it.className.contains("com.comrade.sacco") }
                val lineInfo = appLine?.let { "${it.fileName}:${it.lineNumber}" } ?: "Network/MpesaClient"
                
                Log.e("SavingsFragment", "Network Error at $lineInfo", e)
                Toast.makeText(context, "Network Error at $lineInfo: Check your data/Wi-Fi proxy", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun completeDeposit(amount: Double, type: TransactionType) {
        val user = SessionManager.currentUser ?: return
        lifecycleScope.launch {
            val latestUser = db.userDao().getById(user.id) ?: user
            if (type == TransactionType.DEPOSIT_LOCKED) {
                latestUser.lockedSavings += amount
            } else {
                latestUser.flexWallet += amount
            }
            
            db.userDao().update(latestUser)
            SessionManager.currentUser = latestUser
            recordTransaction(type, amount, "M-Pesa Deposit to ${if (type == TransactionType.DEPOSIT_LOCKED) "Locked" else "Flex"}")
            refreshUI()
        }
    }

    private fun handleFlexWithdrawal() {
        val user = SessionManager.currentUser ?: return
        
        lifecycleScope.launch {
            // Feature: User should be able to withdraw from the flex wallet even if one has any loan
            val latestUser = db.userDao().getById(user.id) ?: user
            val maxWithdrawal = latestUser.flexWallet * latestUser.withdrawalLimitPercent
            if (maxWithdrawal <= 0) {
                Toast.makeText(context, "Insufficient Flex Wallet balance", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val editText = EditText(requireContext())
            editText.hint = "Max KSh %.2f".format(maxWithdrawal)
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

            AlertDialog.Builder(requireContext())
                .setTitle("Withdraw to M-Pesa")
                .setMessage("Funds will be sent to your registered M-Pesa number.")
                .setView(editText)
                .setPositiveButton("Withdraw") { _, _ ->
                    val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                    if (amount > 0 && amount <= maxWithdrawal && amount <= latestUser.flexWallet) {
                        initiateMpesaWithdrawal(amount)
                    } else {
                        Toast.makeText(context, "Invalid amount or exceeds limit", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun initiateMpesaWithdrawal(amount: Double) {
        val user = SessionManager.currentUser ?: return
        
        var phone = user.phoneNumber.replace("+", "").replace(" ", "")
        if (phone.startsWith("0")) {
            phone = "254" + phone.substring(1)
        } else if (phone.startsWith("7") || phone.startsWith("1")) {
            phone = "254" + phone
        }

        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                val auth = MpesaClient.getAuthHeader()
                val tokenResponse = MpesaClient.api.getAccessToken(auth)
                
                val request = B2CRequest(
                    InitiatorName = MpesaClient.INITIATOR_NAME,
                    SecurityCredential = MpesaClient.SECURITY_CREDENTIAL,
                    CommandID = "BusinessPayment",
                    Amount = amount.toInt(),
                    PartyA = MpesaClient.B2C_SHORT_CODE,
                    PartyB = phone,
                    Remarks = "Flex Withdrawal",
                    QueueTimeOutURL = MpesaClient.CALLBACK_URL,
                    ResultURL = MpesaClient.CALLBACK_URL,
                    Occasion = "Withdrawal"
                )

                val response = MpesaClient.api.sendB2CRequest("Bearer ${tokenResponse.access_token}", request)
                
                binding.progressBar.visibility = View.GONE
                
                if (response.ResponseCode == "0") {
                    Toast.makeText(context, "Withdrawal initiated! You will receive an SMS from M-Pesa.", Toast.LENGTH_LONG).show()
                    completeWithdrawal(amount)
                } else {
                    Toast.makeText(context, "M-Pesa Error: ${response.ResponseDescription}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                val appLine = e.stackTrace.find { it.className.contains("com.example.test") || it.className.contains("com.comrade.sacco") }
                val lineInfo = appLine?.let { "${it.fileName}:${it.lineNumber}" } ?: "Network/MpesaClient"
                
                Log.e("SavingsFragment", "Withdrawal Error at $lineInfo", e)
                Toast.makeText(context, "Network Error at $lineInfo: Check your Wi-Fi/Proxy settings", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun completeWithdrawal(amount: Double) {
        val user = SessionManager.currentUser ?: return
        lifecycleScope.launch {
            val latestUser = db.userDao().getById(user.id) ?: user
            latestUser.flexWallet -= amount
            db.userDao().update(latestUser)
            SessionManager.currentUser = latestUser
            recordTransaction(TransactionType.WITHDRAWAL_FLEX, amount, "M-Pesa Withdrawal from Flex")
            refreshUI()
        }
    }

    private fun handleLockedWithdrawal() {
        val user = SessionManager.currentUser ?: return
        
        lifecycleScope.launch {
            // Withdrawal from Locked Savings is still restricted while having loans because it is the collateral.
            val latestUser = db.userDao().getById(user.id) ?: user
            val activeLoans = db.loanDao().getActiveLoansByUserId(latestUser.id)
            if (activeLoans.isNotEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Withdrawal Restricted")
                    .setMessage("You cannot withdraw from Locked Savings while you have an active loan as it serves as security.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            if (latestUser.lockedSavings <= 0) {
                Toast.makeText(context, "No funds in Locked Savings", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val editText = EditText(requireContext())
            editText.hint = "Amount (Max: %.2f)".format(latestUser.lockedSavings)
            editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL

            AlertDialog.Builder(requireContext())
                .setTitle("Request Locked Savings Withdrawal")
                .setMessage("Your request will be submitted to the admin for approval. Once approved, funds will be released after 1 month.")
                .setView(editText)
                .setPositiveButton("Submit Request") { _, _ ->
                    val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                    if (amount > 0 && amount <= latestUser.lockedSavings) {
                        submitLockedWithdrawalRequest(amount)
                    } else {
                        Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun submitLockedWithdrawalRequest(amount: Double) {
        val user = SessionManager.currentUser ?: return
        lifecycleScope.launch {
            val request = WithdrawalRequest(
                id = UUID.randomUUID().toString(),
                userId = user.id,
                userName = user.name,
                amount = amount
            )
            db.withdrawalRequestDao().insert(request)
            
            db.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = "admin",
                title = "New Withdrawal Request",
                content = "${user.name} requested to withdraw KSh $amount from Locked Savings."
            ))

            Toast.makeText(context, "Withdrawal request submitted to admin.", Toast.LENGTH_LONG).show()
        }
    }

    private fun recordTransaction(type: TransactionType, amount: Double, details: String) {
        val user = SessionManager.currentUser ?: return
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            userId = user.id,
            userName = user.name,
            amount = amount,
            type = type,
            timestamp = System.currentTimeMillis(),
            details = details
        )
        lifecycleScope.launch {
            db.transactionDao().insert(transaction)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
