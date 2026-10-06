package com.example.test

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import com.example.test.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var isUserLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(setOf(R.id.loginFragment, R.id.homeFragment, R.id.adminFragment))
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.splashFragment || destination.id == R.id.loginFragment || destination.id == R.id.registrationFragment) {
                binding.textViewUserLoggedIn.visibility = View.GONE
                isUserLoggedIn = false
            } else {
                isUserLoggedIn = true
                val user = SessionManager.currentUser
                if (user != null) {
                    binding.textViewUserLoggedIn.text = getString(R.string.logged_in_as, user.name)
                    binding.textViewUserLoggedIn.visibility = View.VISIBLE
                } else if (destination.id == R.id.adminFragment || destination.id == R.id.userManagementFragment || destination.id == R.id.groupManagementFragment) {
                    binding.textViewUserLoggedIn.text = getString(R.string.logged_in_as, getString(R.string.admin))
                    binding.textViewUserLoggedIn.visibility = View.VISIBLE
                } else {
                    binding.textViewUserLoggedIn.visibility = View.GONE
                    isUserLoggedIn = false
                }
            }

            binding.fab.visibility = View.GONE

            invalidateOptionsMenu()
        }

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, R.string.replace_with_own_action, Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_logout)?.isVisible = isUserLoggedIn
        menu.findItem(R.id.action_notifications)?.isVisible = isUserLoggedIn
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return when (item.itemId) {
            R.id.action_notifications -> {
                navController.navigate(R.id.notificationsFragment)
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun logout() {
        SessionManager.currentUser = null
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.navigate(R.id.loginFragment)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}