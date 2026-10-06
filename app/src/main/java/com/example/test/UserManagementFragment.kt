package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.databinding.FragmentUserManagementBinding
import com.example.test.databinding.ItemUserBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserManagementFragment : Fragment() {

    private var _binding: FragmentUserManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: UserAdapter
    private lateinit var database: AppDatabase
    private val usersList = mutableListOf<User>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        adapter = UserAdapter(usersList) { user, action ->
            when (action) {
                UserAction.BLOCK -> toggleBlock(user)
                UserAction.MESSAGE -> openChatWithUser(user)
            }
        }

        binding.recyclerViewUsers.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewUsers.adapter = adapter

        binding.buttonLogout.setOnClickListener {
            SessionManager.currentUser = null
            findNavController().navigate(R.id.loginFragment)
        }

        loadUsers()
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            val users = database.userDao().getAll()
            usersList.clear()
            usersList.addAll(users)
            adapter.notifyDataSetChanged()
        }
    }

    private fun toggleBlock(user: User) {
        user.isBlocked = !user.isBlocked
        lifecycleScope.launch {
            database.userDao().update(user)
            loadUsers()
            val msg = if (user.isBlocked) "${user.name} Blocked" else "${user.name} Unblocked"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openChatWithUser(user: User) {
        val bundle = Bundle().apply {
            putString("userId", user.id)
        }
        findNavController().navigate(R.id.chatFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class UserAction { BLOCK, MESSAGE }

    inner class UserAdapter(
        private val users: List<User>,
        private val onAction: (User, UserAction) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        inner class UserViewHolder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
            val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return UserViewHolder(binding)
        }

        override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
            val user = users[position]
            holder.binding.textViewUserName.text = user.name
            holder.binding.textViewUserEmail.text = "Email: ${user.email}"
            holder.binding.textViewUserPhone.text = "Phone: ${user.phoneNumber}"
            holder.binding.textViewUserCreated.text = "Joined: ${dateFormat.format(Date(user.createdAt))}"
            
            holder.binding.textViewLimit.visibility = View.GONE
            holder.binding.buttonSetLimit.visibility = View.GONE
            
            holder.binding.buttonBlock.text = if (user.isBlocked) "Unblock" else "Block"
            holder.binding.buttonBlock.setOnClickListener { onAction(user, UserAction.BLOCK) }
            
            holder.binding.buttonMessage.setOnClickListener { onAction(user, UserAction.MESSAGE) }

            holder.binding.buttonDelete.visibility = View.GONE
            
            if (user.isBlocked) {
                holder.binding.textViewUserName.setTextColor(android.graphics.Color.RED)
            } else {
                holder.binding.textViewUserName.setTextColor(android.graphics.Color.BLACK)
            }
        }

        override fun getItemCount() = users.size
    }
}
