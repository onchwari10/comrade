package com.example.test

import android.os.Bundle
import android.util.Patterns
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.test.databinding.FragmentRegistrationBinding
import kotlinx.coroutines.launch
import java.util.*

class RegistrationFragment : Fragment() {

    private var _binding: FragmentRegistrationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegistrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonRegister.setOnClickListener {
            registerUser()
        }

        binding.textViewLogin.setOnClickListener {
            findNavController().navigate(R.id.action_registrationFragment_to_loginFragment)
        }
    }

    private fun registerUser() {
        val name = binding.editTextUsername.text.toString().trim()
        val email = binding.editTextEmail.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()
        val phone = binding.editTextPhoneNumber.text.toString().trim()
        val idNumber = binding.editTextNationalId.text.toString().trim()

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || phone.isEmpty() || idNumber.isEmpty()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(context, "Invalid email address", Toast.LENGTH_SHORT).show()
            return
        }

        if (phone.length < 10) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }

        // Logic to prevent phone number sharing (checking if phone exists)
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            val existingUsers = db.userDao().getAll()
            if (existingUsers.any { it.phoneNumber == phone }) {
                Toast.makeText(context, "This phone number is already registered", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            if (existingUsers.any { it.email == email }) {
                Toast.makeText(context, "This email is already registered", Toast.LENGTH_LONG).show()
                return@launch
            }

            val user = User(
                id = UUID.randomUUID().toString(),
                name = name,
                email = email,
                password = password,
                phoneNumber = phone,
                nationalId = idNumber
            )

            db.userDao().insert(user)
            Toast.makeText(context, "Registration successful!", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_registrationFragment_to_loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
