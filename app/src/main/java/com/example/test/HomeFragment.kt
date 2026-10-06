package com.example.test

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.test.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = SessionManager.currentUser
        if (user == null) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        binding.textViewWelcome.text = "Welcome, ${user.name}!"

        binding.buttonViewSavings.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_savingsFragment)
        }

        binding.buttonViewLoans.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_loansFragment)
        }

        binding.buttonViewGroups.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_groupFragment)
        }

        binding.buttonViewTracker.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_trackerFragment)
        }

        binding.buttonNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_notificationsFragment)
        }

        binding.buttonMessages.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_chatFragment)
        }

        binding.buttonLogout.setOnClickListener {
            SessionManager.currentUser = null
            findNavController().navigate(R.id.loginFragment)
        }

        refreshSummary()
        checkNotifications()
    }

    private fun refreshSummary() {
        val user = SessionManager.currentUser ?: return
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            // Fetch latest user data
            val updatedUser = db.userDao().getById(user.id)
            if (updatedUser != null) {
                // Feature: Dynamically calculate credit score based on the new SACCO formula
                val newScore = CreditScoreManager.calculateScore(updatedUser, db)
                updatedUser.creditScore = newScore
                updatedUser.borrowingMultiplier = CreditScoreManager.getMultiplier(newScore)
                
                db.userDao().update(updatedUser)
                SessionManager.currentUser = updatedUser

                binding.textViewTotalSavings.text = "KSh %.2f".format(updatedUser.totalSavings)
            }
            
            val activeLoans = db.loanDao().getActiveLoansByUserId(user.id)
            binding.textViewActiveLoans.text = "Active Loans: ${activeLoans.size}"
            
            if (activeLoans.any { it.status == LoanStatus.OVERDUE }) {
                binding.textViewActiveLoans.append(" (OVERDUE!)")
                binding.textViewActiveLoans.setTextColor(android.graphics.Color.RED)
            } else {
                binding.textViewActiveLoans.setTextColor(android.graphics.Color.BLUE)
            }
        }
    }

    private fun checkNotifications() {
        val user = SessionManager.currentUser ?: return
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            val unread = db.notificationDao().getUnreadCount(user.id)
            if (unread > 0) {
                binding.textViewNotificationCount.text = unread.toString()
                binding.textViewNotificationCount.visibility = View.VISIBLE
            } else {
                binding.textViewNotificationCount.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
