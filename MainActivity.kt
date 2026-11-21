package com.skillforge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.skillforge.notify.Notifier
import com.skillforge.notify.RescheduleWorker
import com.skillforge.ui.App
import com.skillforge.ui.MainVm
import com.skillforge.ui.SkillForgeTheme
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.channels(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "resched", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build())
        lifecycleScope.launch { vm.repo.rescheduleAll() }
        handle(intent)
        setContent { SkillForgeTheme { Surface(color = MaterialTheme.colorScheme.background) { App(vm) } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }
    private fun handle(i: Intent?) { val id = i?.getLongExtra("goalId", 0L) ?: 0L; if (id > 0) vm.openGoal.value = id }
}
