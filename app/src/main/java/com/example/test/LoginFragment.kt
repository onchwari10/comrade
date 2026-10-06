package com.example.test

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.test.databinding.FragmentLoginBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonLogin.setOnClickListener {
            val identifier = binding.editTextUsername.text.toString().trim()
            val password = binding.editTextPassword.text.toString()

            if (identifier == "admin" && password == "admin") {
                // Set a mock admin user in session
                SessionManager.currentUser = User(
                    id = "admin",
                    name = "Administrator",
                    email = "admin@comrades.com",
                    password = "admin"
                )
                findNavController().navigate(R.id.action_loginFragment_to_adminFragment)
                return@setOnClickListener
            }

            if (identifier.isNotEmpty() && password.isNotEmpty()) {
                val db = AppDatabase.getDatabase(requireContext())
                lifecycleScope.launch {
                    // Try to find user by email or by name (username)
                    val user = db.userDao().getByEmail(identifier) ?: db.userDao().getByName(identifier)
                    
                    if (user != null && user.password == password) {
                        SessionManager.currentUser = user
                        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                    } else {
                        Toast.makeText(context, "Invalid username/email or password", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter credentials", Toast.LENGTH_SHORT).show()
            }
        }

        binding.textViewRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registrationFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
