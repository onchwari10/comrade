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
import com.example.test.databinding.FragmentGroupManagementBinding
import com.example.test.databinding.ItemGroupBinding
import kotlinx.coroutines.launch
import java.util.UUID

class GroupManagementFragment : Fragment() {

    private var _binding: FragmentGroupManagementBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: GroupAdapter
    private lateinit var database: AppDatabase
    private val groupsList = mutableListOf<Group>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = AppDatabase.getDatabase(requireContext())

        val currentUser = SessionManager.currentUser
        
        // System Admin View: Hide add group controls
        if (currentUser == null) {
            binding.buttonAddGroup.visibility = View.GONE
            binding.editTextGroupName.visibility = View.GONE
        }

        adapter = GroupAdapter(groupsList, currentUser, 
            onManage = { group -> manageGroup(group, currentUser) },
            onDelete = { group -> deleteGroup(group) }
        )

        binding.recyclerViewGroups.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewGroups.adapter = adapter

        binding.buttonAddGroup.setOnClickListener {
            addGroup()
        }

        loadGroups()
    }

    private fun loadGroups() {
        lifecycleScope.launch {
            val groups = database.groupDao().getAll()
            groupsList.clear()
            groupsList.addAll(groups)
            adapter.notifyDataSetChanged()
        }
    }

    private fun addGroup() {
        val name = binding.editTextGroupName.text.toString()
        val currentUser = SessionManager.currentUser
        if (name.isNotEmpty() && currentUser != null) {
            val newGroup = Group(
                id = UUID.randomUUID().toString(),
                name = name,
                creatorId = currentUser.id,
                members = mutableListOf(currentUser.id)
            )
            lifecycleScope.launch {
                database.groupDao().insert(newGroup)
                loadGroups()
                binding.editTextGroupName.text.clear()
                Toast.makeText(requireContext(), R.string.group_created, Toast.LENGTH_SHORT).show()
            }
        } else if (currentUser == null) {
            Toast.makeText(requireContext(), R.string.error_no_session, Toast.LENGTH_SHORT).show()
        }
    }

    private fun manageGroup(group: Group, currentUser: User?) {
        if (currentUser == null) {
            // System Admin: Direct members list view
            showMembersDialog(group)
        } else {
            val options = mutableListOf<String>()
            options.add("View Members")

            // Only group creator can approve requests/loans
            if (group.creatorId == currentUser.id) {
                options.add("Approve Join Requests")
                options.add("Approve Loan Applications")
            }

            AlertDialog.Builder(requireContext())
                .setTitle(group.name)
                .setItems(options.toTypedArray()) { _, which ->
                    when (options[which]) {
                        "View Members" -> showMembersDialog(group)
                        "Approve Join Requests" -> showPendingMembersDialog(group)
                        "Approve Loan Applications" -> showPendingLoansDialog(group)
                    }
                }
                .show()
        }
    }

    private fun showMembersDialog(group: Group) {
        lifecycleScope.launch {
            val memberIds = group.members.toList()
            val members = memberIds.mapNotNull { database.userDao().getById(it) }
            val memberNames = members.map { it.name }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle(group.name + " - Members")
                .setItems(memberNames, null)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun showPendingMembersDialog(group: Group) {
        if (group.pendingRequests.isEmpty()) {
            Toast.makeText(requireContext(), "No pending requests", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val pendingUserIds = group.pendingRequests.toList()
            val pendingUsers = pendingUserIds.mapNotNull { database.userDao().getById(it) }
            val userNames = pendingUsers.map { it.name }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Pending Requests")
                .setItems(userNames) { _, index ->
                    val selectedUser = pendingUsers[index]
                    approveUser(group, selectedUser)
                }
                .show()
        }
    }

    private fun approveUser(group: Group, user: User) {
        group.pendingRequests.remove(user.id)
        group.members.add(user.id)
        lifecycleScope.launch {
            database.groupDao().update(group)
            loadGroups()
            Toast.makeText(requireContext(), "${user.name} approved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPendingLoansDialog(group: Group) {
        lifecycleScope.launch {
            val loans = database.loanDao().getByGroupId(group.id).filter { it.status == LoanStatus.PENDING }
            if (loans.isEmpty()) {
                Toast.makeText(requireContext(), "No pending loans for this group", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val loanSummaries = loans.map { "${it.userName}: KSh ${it.amount}" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Approve Group Loans")
                .setItems(loanSummaries) { _, index ->
                    val loan = loans[index]
                    approveLoan(loan)
                }
                .show()
        }
    }

    private fun approveLoan(loan: Loan) {
        loan.status = LoanStatus.APPROVED
        lifecycleScope.launch {
            database.loanDao().update(loan)
            Toast.makeText(requireContext(), "Loan for ${loan.userName} approved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteGroup(group: Group) {
        lifecycleScope.launch {
            database.groupDao().delete(group)
            loadGroups()
            Toast.makeText(requireContext(), getString(R.string.group_deleted, group.name), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class GroupAdapter(
        private val groups: List<Group>,
        private val currentUser: User?,
        private val onManage: (Group) -> Unit,
        private val onDelete: (Group) -> Unit
    ) : RecyclerView.Adapter<GroupAdapter.GroupViewHolder>() {

        inner class GroupViewHolder(val binding: ItemGroupBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
            val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GroupViewHolder(binding)
        }

        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
            val group = groups[position]
            val context = holder.itemView.context
            holder.binding.textViewGroupName.text = group.name
            holder.binding.textViewMemberCount.text = context.getString(R.string.member_count, group.members.size)
            
            // System admin view: Use "View Members" instead of "Manage"
            if (currentUser == null) {
                holder.binding.buttonManageGroup.text = "View Members"
            } else {
                holder.binding.buttonManageGroup.text = "Manage"
            }
            
            holder.binding.buttonManageGroup.visibility = View.VISIBLE
            holder.binding.buttonManageGroup.setOnClickListener { onManage(group) }
            
            // System admin can see manage but it will only show members (handled in manageGroup)
            // Group creators can delete their own groups
            if (currentUser != null && group.creatorId == currentUser.id) {
                holder.binding.buttonDeleteGroup.visibility = View.VISIBLE
                holder.binding.buttonDeleteGroup.setOnClickListener { onDelete(group) }
            } else {
                holder.binding.buttonDeleteGroup.visibility = View.GONE
            }
        }

        override fun getItemCount() = groups.size
    }
}
