package com.example.buzztimer.activity

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.buzztimer.R
import com.example.buzztimer.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAboutBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up action bar
        supportActionBar?.apply {
            title = getString(R.string.about)
            setDisplayHomeAsUpEnabled(true)
        }
        
        // Set app version
        setupAppInfo()
    }
    
    private fun setupAppInfo() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val version = packageInfo.versionName
            binding.tvAppVersion.text = getString(R.string.version_format, version)
        } catch (e: PackageManager.NameNotFoundException) {
            binding.tvAppVersion.text = getString(R.string.version_format, "1.0.0")
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
