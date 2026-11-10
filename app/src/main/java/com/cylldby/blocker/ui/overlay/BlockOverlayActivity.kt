package com.cylldby.blocker.ui.overlay

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cylldby.blocker.R
import com.cylldby.blocker.databinding.ActivityBlockOverlayBinding
import com.cylldby.blocker.service.BlockerAccessibilityService

class BlockOverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockOverlayBinding

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reason = intent.getStringExtra(BlockerAccessibilityService.EXTRA_REASON)
        if (reason == BlockerAccessibilityService.REASON_SCHEDULE) {
            binding.tvBlockedMessage.text = getString(R.string.blocked_scheduled_message)
        }

        binding.btnGoBack.setOnClickListener { finish() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back navigation to prevent returning to the blocked app
    }
}
