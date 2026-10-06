package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.test.databinding.FragmentAdminBinding
import kotlinx.coroutines.launch

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonManageUsers.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_userManagementFragment)
        }

        binding.buttonManageGroups.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_groupManagementFragment)
        }

        binding.buttonManageLoans.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_loanManagementFragment)
        }

        binding.buttonMonitorTransactions.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_transactionMonitoringFragment)
        }

        binding.buttonManageApprovals.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_adminWithdrawalApprovalFragment)
        }

        binding.buttonAdminNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_notificationsFragment)
        }

        binding.buttonAdminMessages.setOnClickListener {
            findNavController().navigate(R.id.action_adminFragment_to_chatFragment)
        }

        binding.buttonLogout.setOnClickListener {
            SessionManager.currentUser = null
            findNavController().navigate(R.id.loginFragment)
        }

        checkAdminNotifications()
    }

    private fun checkAdminNotifications() {
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            val unread = db.notificationDao().getUnreadCount("admin")
            if (unread > 0) {
                binding.textViewAdminNotificationCount.text = unread.toString()
                binding.textViewAdminNotificationCount.visibility = View.VISIBLE
            } else {
                binding.textViewAdminNotificationCount.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
