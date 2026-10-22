package com.elevate.launcher.ui.drawer

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.elevate.launcher.data.AppRepository
import com.elevate.launcher.databinding.ActivityAppDrawerBinding
import kotlin.concurrent.thread

class AppDrawerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_QUERY = "initial_query"
    }

    private lateinit var binding: ActivityAppDrawerBinding
    private lateinit var adapter: AppListAdapter
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDrawerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AppListAdapter { row ->
            launchApp(row.item.packageName, row.item.activityName)
        }
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter

        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY)
        if (!initialQuery.isNullOrBlank()) {
            binding.editSearch.setText(initialQuery)
        }

        loadApps()
    }

    private fun loadApps() {
        thread {
            val rows = AppRepository(applicationContext).loadGroupedApps()
            mainHandler.post {
                adapter.submitList(rows)
                val q = binding.editSearch.text?.toString().orEmpty()
                if (q.isNotBlank()) adapter.filter(q)
            }
        }
    }

    private fun launchApp(packageName: String, activityName: String) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
    }
}
