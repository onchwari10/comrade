package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.databinding.FragmentChatBinding
import com.example.test.databinding.ItemMessageBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var adapter: MessageAdapter
    private val currentUser = SessionManager.currentUser
    private var selectedUserId: String? = null
    private var selectedUserName: String? = null
    private var isAutoRefreshRunning = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        adapter = MessageAdapter(mutableListOf())
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = adapter

        val user = currentUser ?: return
        
        binding.buttonSelectUser.visibility = View.VISIBLE
        binding.buttonSelectUser.setOnClickListener {
            showUserSelectionDialog()
        }

        val argUserId = arguments?.getString("userId")
        if (argUserId != null) {
            selectedUserId = argUserId
            lifecycleScope.launch {
                val targetUser = if (argUserId == "admin") {
                    User("admin", "Support (Admin)", "admin@comrades.com", "admin")
                } else {
                    db.userDao().getById(argUserId)
                }
                selectedUserName = targetUser?.name ?: "User"
                binding.textViewChatWith.text = "Chatting with $selectedUserName"
                loadMessages()
            }
        } else if (user.id != "admin") {
            selectedUserId = "admin"
            selectedUserName = "Support (Admin)"
            binding.textViewChatWith.text = "Chatting with $selectedUserName"
            loadMessages()
        } else {
            binding.textViewChatWith.text = "Select a user to chat"
        }

        binding.buttonSend.setOnClickListener {
            sendMessage()
        }

        startAutoRefresh()
    }

    private fun showUserSelectionDialog() {
        lifecycleScope.launch {
            val user = currentUser ?: return@launch
            val displayUsers = mutableListOf<Pair<String, String>>() 

            if (user.id != "admin") {
                displayUsers.add("admin" to "Support (Admin)")
            }

            // Find members of all groups the user belongs to
            val allGroups = db.groupDao().getAll()
            val userGroups = allGroups.filter { it.members.contains(user.id) }
            val potentialChatPartners = mutableSetOf<String>()

            if (user.id == "admin") {
                // Admin can chat with everyone
                db.userDao().getAll().filter { it.id != "admin" }.forEach {
                    displayUsers.add(it.id to it.name)
                }
            } else {
                userGroups.forEach { group ->
                    group.members.forEach { memberId ->
                        if (memberId != user.id) {
                            potentialChatPartners.add(memberId)
                        }
                    }
                }
                
                potentialChatPartners.forEach { memberId ->
                    val member = db.userDao().getById(memberId)
                    if (member != null) {
                        displayUsers.add(member.id to member.name)
                    }
                }
            }

            if (displayUsers.isEmpty()) {
                Toast.makeText(context, "No other users available in your groups", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val userNames = displayUsers.map { it.second }.toTypedArray()
            
            AlertDialog.Builder(requireContext())
                .setTitle("Select recipient")
                .setItems(userNames) { _, which ->
                    val selection = displayUsers[which]
                    selectedUserId = selection.first
                    selectedUserName = selection.second
                    binding.textViewChatWith.text = "Chatting with $selectedUserName"
                    loadMessages()
                }
                .show()
        }
    }

    private fun loadMessages() {
        val user = currentUser ?: return
        val targetId = selectedUserId ?: return

        lifecycleScope.launch {
            val messages = db.messageDao().getChatHistoryBetween(user.id, targetId)
            adapter.updateMessages(messages)
            if (messages.isNotEmpty()) {
                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun startAutoRefresh() {
        if (isAutoRefreshRunning) return
        isAutoRefreshRunning = true
        lifecycleScope.launch {
            while (isAutoRefreshRunning) {
                if (selectedUserId != null) {
                    loadMessages()
                }
                delay(3000)
            }
        }
    }

    private fun sendMessage() {
        val user = currentUser ?: return
        val targetId = selectedUserId ?: run {
            Toast.makeText(context, "Please select a recipient", Toast.LENGTH_SHORT).show()
            return
        }
        val content = binding.editTextMessage.text.toString().trim()
        
        if (content.isEmpty()) return

        val message = Message(
            id = UUID.randomUUID().toString(),
            senderId = user.id,
            senderName = user.name,
            receiverId = targetId,
            content = content
        )

        lifecycleScope.launch {
            db.messageDao().insert(message)
            binding.editTextMessage.setText("")
            loadMessages()
            
            db.notificationDao().insert(Notification(
                id = UUID.randomUUID().toString(),
                userId = targetId,
                title = "New Message",
                content = "From ${user.name}: ${if (content.length > 20) content.take(20) + "..." else content}",
                targetFragmentId = R.id.chatFragment,
                metadata = user.id // Pass sender ID for redirection
            ))
        }
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        isAutoRefreshRunning = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        isAutoRefreshRunning = false
    }

    inner class MessageAdapter(private var messages: List<Message>) :
        RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]
            holder.binding.textViewMessage.text = message.content
            holder.binding.textViewSender.text = message.senderName
            
            if (message.senderId == currentUser?.id) {
                holder.binding.root.layoutDirection = View.LAYOUT_DIRECTION_RTL
                holder.binding.cardMessage.setCardBackgroundColor(android.graphics.Color.parseColor("#DCF8C6"))
                holder.binding.textViewSender.visibility = View.GONE
            } else {
                holder.binding.root.layoutDirection = View.LAYOUT_DIRECTION_LTR
                holder.binding.cardMessage.setCardBackgroundColor(android.graphics.Color.WHITE)
                holder.binding.textViewSender.visibility = View.VISIBLE
            }
        }

        override fun getItemCount() = messages.size

        fun updateMessages(newMessages: List<Message>) {
            if (this.messages != newMessages) {
                this.messages = newMessages
                notifyDataSetChanged()
            }
        }
    }
}
