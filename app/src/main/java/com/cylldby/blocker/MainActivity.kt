package com.cylldby.blocker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cylldby.blocker.databinding.ActivityMainBinding
import com.cylldby.blocker.onboarding.OnboardingActivity
import com.cylldby.blocker.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var onboardingLaunched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(navHost.navController)
    }

    override fun onResume() {
        super.onResume()
        // Only launch onboarding once per session — avoids a redirect loop when
        // returning from system settings screens (e.g. Accessibility Settings).
        if (!onboardingLaunched && !PermissionHelper.guardPermissionsGranted(this)) {
            onboardingLaunched = true
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        // Reset flag so the check runs again next time the app is opened cold.
        if (PermissionHelper.guardPermissionsGranted(this)) {
            onboardingLaunched = false
        }
    }
}
