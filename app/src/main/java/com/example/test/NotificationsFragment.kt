package com.example.test

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.test.databinding.FragmentNotificationsBinding
import com.example.test.databinding.ItemNotificationBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        adapter = NotificationAdapter(mutableListOf())
        binding.recyclerViewNotifications.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewNotifications.adapter = adapter

        loadNotifications()
    }

    private fun loadNotifications() {
        val user = SessionManager.currentUser ?: return
        val targetId = if (user.email == "admin@comrades.com") "admin" else user.id
        
        lifecycleScope.launch {
            val notifications = db.notificationDao().getByUserId(targetId)
            adapter.updateNotifications(notifications)
            
            // Mark all as read
            notifications.forEach {
                if (!it.isRead) {
                    it.isRead = true
                    db.notificationDao().update(it)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class NotificationAdapter(private var notifications: List<Notification>) :
        RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        inner class ViewHolder(val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val notification = notifications[position]
            holder.binding.textViewTitle.text = notification.title
            holder.binding.textViewContent.text = notification.content
            holder.binding.textViewTime.text = dateFormat.format(Date(notification.timestamp))
            
            if (notification.isRead) {
                holder.binding.root.alpha = 0.6f
            } else {
                holder.binding.root.alpha = 1.0f
            }

            holder.binding.root.setOnClickListener {
                notification.targetFragmentId?.let { fragmentId ->
                    val bundle = Bundle().apply {
                        if (fragmentId == R.id.groupFragment) {
                            putString("groupId", notification.metadata)
                        } else if (fragmentId == R.id.chatFragment) {
                            putString("userId", notification.metadata)
                        }
                    }
                    try {
                        findNavController().navigate(fragmentId, bundle)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        override fun getItemCount() = notifications.size

        fun updateNotifications(newNotifications: List<Notification>) {
            notifications = newNotifications
            notifyDataSetChanged()
        }
    }
}
