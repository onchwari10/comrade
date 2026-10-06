package com.example.test

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.test.databinding.FragmentTrackerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class TrackerFragment : Fragment() {

    private var _binding: FragmentTrackerBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TransactionAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TransactionAdapter(mutableListOf())
        binding.recyclerViewTransactions.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewTransactions.adapter = adapter

        binding.buttonDownloadStatement.setOnClickListener {
            generatePdfStatement()
        }

        refreshData()
    }

    private fun refreshData() {
        val user = SessionManager.currentUser ?: return
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            val transactions = db.transactionDao().getByUserId(user.id)
            adapter.updateData(transactions)

            var income = 0.0
            var expenses = 0.0
            transactions.forEach {
                val isOutflow = it.type == TransactionType.WITHDRAWAL_FLEX || 
                               it.type == TransactionType.CONTRIBUTION_GROUP ||
                               it.type == TransactionType.LOAN_REPAYMENT
                
                val isInflow = it.type == TransactionType.DEPOSIT_LOCKED ||
                              it.type == TransactionType.DEPOSIT_FLEX ||
                              it.type == TransactionType.LOAN_DISBURSEMENT
                
                if (isOutflow) expenses += it.amount 
                else if (isInflow) income += it.amount
            }

            binding.textViewIncomeAmount.text = "KSh %.2f".format(income)
            binding.textViewExpenseAmount.text = "KSh %.2f".format(expenses)
        }
    }

    private fun generatePdfStatement() {
        val user = SessionManager.currentUser ?: return
        val context = requireContext()
        val db = AppDatabase.getDatabase(context)

        lifecycleScope.launch {
            val transactions = db.transactionDao().getByUserId(user.id)
                .sortedByDescending { it.timestamp }
                .take(20)

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()
            val titlePaint = Paint()

            var y = 40f

            titlePaint.textSize = 18f
            titlePaint.isFakeBoldText = true
            canvas.drawText("Student Financial Health Report", 40f, y, titlePaint)
            y += 30f

            paint.textSize = 12f
            canvas.drawText("User: ${user.name}", 40f, y, paint)
            y += 20f
            canvas.drawText("Credit Score: ${user.creditScore}", 40f, y, paint)
            y += 20f
            canvas.drawText("Date Generated: ${dateFormat.format(Date())}", 40f, y, paint)
            y += 20f
            canvas.drawText("------------------------------------------------------------", 40f, y, paint)
            y += 30f

            if (transactions.isEmpty()) {
                canvas.drawText("No transactions found.", 40f, y, paint)
            } else {
                transactions.forEach {
                    val isOutflow = it.type == TransactionType.WITHDRAWAL_FLEX || 
                                   it.type == TransactionType.CONTRIBUTION_GROUP ||
                                   it.type == TransactionType.LOAN_REPAYMENT
                    val type = if (isOutflow) "DR" else "CR"
                    val dateStr = dateFormat.format(Date(it.timestamp))
                    
                    canvas.drawText("$dateStr | ${it.details}", 40f, y, paint)
                    y += 15f
                    canvas.drawText("Amount: KSh ${"%.2f".format(it.amount)} ($type)", 40f, y, paint)
                    y += 15f
                    canvas.drawText("------------------------------------------------------------", 40f, y, paint)
                    y += 25f

                    // Simple page overflow check
                    if (y > 800) {
                        // In a real app, we'd start a new page here. 
                        // For a mini-statement, 20 items usually fit.
                        return@forEach
                    }
                }
            }

            pdfDocument.finishPage(page)

            val fileName = "Statement_${System.currentTimeMillis()}.pdf"
            
            withContext(Dispatchers.IO) {
                try {
                    val outputStream: OutputStream?
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        outputStream = uri?.let { context.contentResolver.openOutputStream(it) }
                    } else {
                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val file = File(downloadsDir, fileName)
                        outputStream = FileOutputStream(file)
                    }

                    outputStream?.use { 
                        pdfDocument.writeTo(it)
                    }
                    pdfDocument.close()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Statement saved to Downloads folder", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
