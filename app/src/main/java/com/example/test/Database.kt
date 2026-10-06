package com.example.test

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    suspend fun getAll(): List<User>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: String): User?

    @Query("SELECT * FROM users WHERE email = :email")
    suspend fun getByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE name = :name")
    suspend fun getByName(name: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups")
    suspend fun getAll(): List<Group>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: String): Group?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: Group)

    @Update
    suspend fun update(group: Group)

    @Delete
    suspend fun delete(group: Group)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans")
    suspend fun getAll(): List<Loan>
    
    @Query("SELECT * FROM loans WHERE groupId = :groupId")
    suspend fun getByGroupId(groupId: String): List<Loan>

    @Query("SELECT * FROM loans WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<Loan>

    @Query("SELECT * FROM loans WHERE userId = :userId AND (status = 'APPROVED' OR status = 'OVERDUE')")
    suspend fun getActiveLoansByUserId(userId: String): List<Loan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(loan: Loan)

    @Update
    suspend fun update(loan: Loan)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<Transaction>

    @Query("SELECT * FROM transactions WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction)
}

@Dao
interface WithdrawalRequestDao {
    @Query("SELECT * FROM withdrawal_requests")
    suspend fun getAll(): List<WithdrawalRequest>

    @Query("SELECT * FROM withdrawal_requests WHERE userId = :userId")
    suspend fun getByUserId(userId: String): List<WithdrawalRequest>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: WithdrawalRequest)

    @Update
    suspend fun update(request: WithdrawalRequest)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (senderId = :id1 AND receiverId = :id2) OR (senderId = :id2 AND receiverId = :id1) ORDER BY timestamp ASC")
    suspend fun getChatHistoryBetween(id1: String, id2: String): List<Message>

    @Query("SELECT * FROM messages WHERE receiverId = :userId OR senderId = :userId ORDER BY timestamp ASC")
    suspend fun getChatHistory(userId: String): List<Message>

    @Query("SELECT * FROM messages WHERE receiverId = 'admin' OR senderId = 'admin' ORDER BY timestamp ASC")
    suspend fun getAdminMessages(): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getByUserId(userId: String): List<Notification>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadCount(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: Notification)

    @Update
    suspend fun update(notification: Notification)
}

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: String): MutableList<String> {
        val listType = object : TypeToken<MutableList<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun toStringList(list: MutableList<String>): String {
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromStringMap(value: String): MutableMap<String, Double> {
        val mapType = object : TypeToken<MutableMap<String, Double>>() {}.type
        return gson.fromJson(value, mapType)
    }

    @TypeConverter
    fun toStringMap(map: MutableMap<String, Double>): String {
        return gson.toJson(map)
    }

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name
    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromLoanType(value: String): LoanType = LoanType.valueOf(value)
    @TypeConverter
    fun toLoanType(value: LoanType): String = value.name

    @TypeConverter
    fun fromLoanStatus(value: String): LoanStatus = LoanStatus.valueOf(value)
    @TypeConverter
    fun toLoanStatus(value: LoanStatus): String = value.name

    @TypeConverter
    fun fromRequestStatus(value: RequestStatus): String = value.name
    @TypeConverter
    fun toRequestStatus(value: String): RequestStatus = RequestStatus.valueOf(value)
}

@Database(entities = [User::class, Group::class, Loan::class, Transaction::class, WithdrawalRequest::class, Message::class, Notification::class], version = 13, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun groupDao(): GroupDao
    abstract fun loanDao(): LoanDao
    abstract fun transactionDao(): TransactionDao
    abstract fun withdrawalRequestDao(): WithdrawalRequestDao
    abstract fun messageDao(): MessageDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comrade_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
