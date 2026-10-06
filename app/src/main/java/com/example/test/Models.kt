package com.example.test

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import kotlin.random.Random

enum class LoanType { INDIVIDUAL, GROUP }
enum class LoanStatus { PENDING, APPROVED, REJECTED, PAID, OVERDUE }
enum class TransactionType { 
    DEPOSIT_LOCKED, 
    DEPOSIT_FLEX, 
    WITHDRAWAL_FLEX, 
    WITHDRAWAL_LOCKED,
    LOAN_DISBURSEMENT, 
    LOAN_REPAYMENT, 
    CONTRIBUTION_GROUP,
    TRANSFER_LOCKED_TO_FLEX,
    TRANSFER_FLEX_TO_LOCKED
}

enum class RequestStatus { PENDING, APPROVED, REJECTED, COMPLETED }

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val password: String,
    val phoneNumber: String = "",
    val nationalId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    var isBlocked: Boolean = false,
    var withdrawalLimitPercent: Double = 0.3, // 30% - 50% limit for Flex Wallet
    var withdrawalLimit: Double = 1000.0, // Daily withdrawal limit in KSh
    var lockedSavings: Double = 0.0,
    var flexWallet: Double = 0.0,
    var creditScore: Int = 300, // Initial score
    var borrowingMultiplier: Double = 1.5 // 1.5x to 3x based on credit score
) {
    val totalSavings: Double get() = lockedSavings + flexWallet
    val maxBorrowingPower: Double get() = lockedSavings * borrowingMultiplier
}

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey val id: String,
    val name: String,
    val creatorId: String,
    val members: MutableList<String> = mutableListOf(), // User IDs
    val pendingRequests: MutableList<String> = mutableListOf(), // User IDs
    val contributions: MutableMap<String, Double> = mutableMapOf(), // User ID to Amount
    var totalGroupContribution: Double = 0.0,
    var groupCreditScore: Int = 300
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val amount: Double,
    var remainingAmount: Double,
    val interestRate: Double,
    val type: LoanType = LoanType.INDIVIDUAL,
    val groupId: String? = null,
    var status: LoanStatus = LoanStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long,
    var paidAt: Long? = null // New field for scoring logic
)

@Entity(tableName = "withdrawal_requests")
data class WithdrawalRequest(
    @PrimaryKey val id: String,
    val userId: String,
    val userName: String,
    val amount: Double,
    var status: RequestStatus = RequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    var approvedAt: Long? = null,
    var releaseDate: Long? = null, // Individual: 1 month after approval. Chama: immediate upon approval.
    val groupId: String? = null
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String,
    val senderId: String,
    val senderName: String,
    val receiverId: String, // "admin" or userId
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false
)

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey val id: String,
    val userId: String, // "admin" or userId
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    var targetFragmentId: Int? = null,
    var metadata: String? = null // Stores extra data like userId for Chat, groupId, etc.
)

object SessionManager {
    var currentUser: User? = null
}

object IdGenerator {
    fun generateAlphanumericId(length: Int = 5): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}
