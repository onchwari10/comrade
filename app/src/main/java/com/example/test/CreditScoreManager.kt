package com.example.test

import kotlin.math.max
import kotlin.math.min

object CreditScoreManager {

    suspend fun calculateScore(user: User, db: AppDatabase): Int {
        var earnedPoints = 0
        val currentTime = System.currentTimeMillis()

        // 1. Membership Duration (Max 100)
        val membershipMillis = currentTime - user.createdAt
        val months = membershipMillis / (30L * 24 * 60 * 60 * 1000)
        
        // Award points based on duration
        // To start at exactly 300, the first 6 months award 0 extra points
        earnedPoints += when {
            months >= 24 -> 100
            months >= 12 -> 80
            months >= 6 -> 50
            else -> 0 
        }

        // 2. Savings Discipline (Max 150)
        val transactions = db.transactionDao().getByUserId(user.id)
        val deposits = transactions.filter { it.type == TransactionType.DEPOSIT_LOCKED || it.type == TransactionType.DEPOSIT_FLEX }
        val withdrawals = transactions.filter { it.type == TransactionType.WITHDRAWAL_FLEX || it.type == TransactionType.WITHDRAWAL_LOCKED }

        if (deposits.isNotEmpty()) {
            // Saves consistently (At least one deposit in last 30 days)
            val hasRecentDeposit = deposits.any { it.timestamp > (currentTime - 30L * 24 * 60 * 60 * 1000) }
            if (hasRecentDeposit) earnedPoints += 100

            // Savings growing (Non-zero balance)
            if (user.totalSavings > 0) earnedPoints += 30

            // Low withdrawal frequency
            val recentWithdrawals = withdrawals.filter { it.timestamp > (currentTime - 30L * 24 * 60 * 60 * 1000) }
            if (recentWithdrawals.size <= 1) earnedPoints += 20
        }

        // 3. Loan Repayment History (Max 200)
        val loans = db.loanDao().getByUserId(user.id)
        val paidLoans = loans.filter { it.status == LoanStatus.PAID }
        val overdueLoans = loans.filter { it.status == LoanStatus.OVERDUE }

        if (paidLoans.isNotEmpty()) {
            var onTimeCount = 0
            var earlyCount = 0
            
            paidLoans.forEach { loan ->
                val paidAt = loan.paidAt ?: loan.createdAt // Fallback if paidAt missing
                val halfPeriod = loan.createdAt + (loan.dueDate - loan.createdAt) / 2
                
                if (paidAt <= loan.dueDate) onTimeCount++
                if (paidAt <= halfPeriod) earlyCount++
            }

            if (onTimeCount > 0) earnedPoints += 150 // Pays loans on time
            if (earlyCount > 0) earnedPoints += 30    // Early repayments
            if (overdueLoans.isEmpty()) earnedPoints += 20 // No missed payments
        }

        // 4. Loan Utilization (Max 100)
        val activeLoan = loans.find { (it.status == LoanStatus.APPROVED || it.status == LoanStatus.OVERDUE) && it.remainingAmount > 0 }
        
        if (user.lockedSavings > 0) {
            if (activeLoan == null) {
                earnedPoints += 100 // 0% utilization
            } else {
                val limit = user.lockedSavings * user.borrowingMultiplier
                if (limit > 0) {
                    val utilization = (activeLoan.amount / limit)
                    earnedPoints += when {
                        utilization < 0.5 -> 100
                        utilization <= 0.8 -> 50
                        else -> 20
                    }
                }
            }
        }

        // 5. Penalties
        var penalty = 0
        if (overdueLoans.isNotEmpty()) penalty += 50

        val finalScore = 300 + earnedPoints - penalty
        
        // Ensure score stays within 300 - 850 range
        return max(300, min(850, finalScore))
    }

    fun getMultiplier(score: Int): Double {
        return when {
            score >= 800 -> 3.0
            score >= 700 -> 2.5
            score >= 600 -> 2.0
            else -> 1.5
        }
    }
}
