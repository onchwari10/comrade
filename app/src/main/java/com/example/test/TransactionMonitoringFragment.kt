package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test.databinding.FragmentTransactionMonitoringBinding
import kotlinx.coroutines.launch

class TransactionMonitoringFragment : Fragment() {

    private var _binding: FragmentTransactionMonitoringBinding? = null
    private val binding get() = _binding!!
    private lateinit var database: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransactionMonitoringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        binding.recyclerViewTransactions.layoutManager = LinearLayoutManager(context)
        
        loadTransactions()
    }

    private fun loadTransactions() {
        lifecycleScope.launch {
            val transactions = database.transactionDao().getAll()
            binding.recyclerViewTransactions.adapter = TransactionAdapter(transactions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}