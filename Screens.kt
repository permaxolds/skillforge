@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.skillforge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.skillforge.data.*
import com.skillforge.notify.Notifier
import com.skillforge.notify.Scheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Dark = darkColorScheme(primary = Color(0xFF8B5CF6), secondary = Color(0xFF22D3EE), background = Color(0xFF0A0C11),
    surface = Color(0xFF12151D), surfaceVariant = Color(0xFF1A1E29), onBackground = Color(0xFFE7E9EE), onSurface = Color(0xFFE7E9EE))
@Composable fun SkillForgeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Dark, content = content)

private val CATS = listOf("C++ Programming", "Python Programming", "Data Structures & Algorithms", "AI & Machine Learning", "Web Development",
    "App Development", "Projects & Prototyping", "College Academics", "English Communication", "Problem Solving", "Personal Development")
private val PRI = listOf("Low", "Medium", "High")
private val FREQ = listOf("DAILY", "WEEKLY", "MONTHLY", "CUSTOM", "ONCE")

@Composable fun SfCard(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp), content = content) }

@Composable fun App(vm: MainVm) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val open by vm.openGoal.collectAsState()
    val ctx = LocalContext.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            perm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(open) { if (open != null) tab = 1 }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar {
            listOf("🏠" to "Home", "🎯" to "Goals", "🤖" to "AI Chat", "⚙️" to "Settings").forEachIndexed { i, (e, l) ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(e) }, label = { Text(l) })
            }
        }
    }) { p ->
        Box(Modifier.padding(p).statusBarsPadding()) {
            when (tab) { 0 -> Home(vm) { tab = 2 }; 1 -> GoalsScreen(vm); 2 -> ChatScreen(vm); else -> SettingsScreen(vm) }
        }
    }
}

@Composable fun Bar(p: Int) = LinearProgressIndicator(progress = { p / 100f }, Modifier.fillMaxWidth().height(8.dp), trackColor = Color.White.copy(0.1f))

@Composable fun Bars(vals: List<Int>, labels: List<String>) = Row(Modifier.fillMaxWidth().height(110.dp), Arrangement.spacedBy(8.dp), Alignment.Bottom) {
    vals.forEachIndexed { i, v ->
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height((80 * maxOf(v, 3) / 100).dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (v == 0) 0.25f else 1f), RoundedCornerShape(6.dp)))
            Text(labels[i], fontSize = 10.sp, color = Color.White.copy(0.5f))
        }
    }
}

@Composable fun TaskRow(vm: MainVm, g: Goal, k: String, done: Boolean) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Checkbox(done, { vm.setDone(g, k, it) })
    Column(Modifier.weight(1f)) {
        Text(g.title, fontWeight = FontWeight.Medium)
        Text(g.category + (g.reminderMin?.let { " • ⏰ %02d:%02d".format(it / 60, it % 60) } ?: ""), fontSize = 12.sp, color = Color.White.copy(0.5f))
    }
}

@Composable fun Home(vm: MainVm, toChat: () -> Unit) {
    val goals by vm.goals.collectAsState(); val comps by vm.comps.collectAsState()
    val t = LocalDate.now(); val m = remember(comps) { Marks.of(comps) }
    val todayOcc = remember(goals, m) { Stats.occ(goals.filter { it.frequency !in listOf("WEEKLY", "MONTHLY") }, m, t, t) }
    val week = remember(goals, m) { Stats.occ(goals, m, Recur.monday(t), t) }
    val month = remember(goals, m) { Stats.occ(goals, m, t.withDayOfMonth(1), t) }
    val streak = remember(goals, m) { Stats.streak(goals, m, t) }
    val bars = remember(goals, m) { (6 downTo 0).map { Stats.pct(Stats.occ(goals, m, t.minusDays(it.toLong()), t.minusDays(it.toLong()))) } }
    val labels = (6 downTo 0).map { t.minusDays(it.toLong()).dayOfWeek.name.take(1) }
    val total = comps.count { !it.skipped }
    val hr = java.time.LocalTime.now().hour
    val pend = todayOcc.count { !it.done }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column {
            Text("Good " + (if (hr < 12) "morning" else if (hr < 17) "afternoon" else "evening") + " 👋", style = MaterialTheme.typography.headlineMedium)
            Text("${t.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${t.dayOfMonth} ${t.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${t.year}", color = Color.White.copy(0.5f))
        } }
        item { SfCard {
            Text("Today ${Stats.pct(todayOcc)}%", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Bar(Stats.pct(todayOcc))
            Text("${todayOcc.count { it.done }} done • $pend pending", fontSize = 12.sp, color = Color.White.copy(0.5f))
            Spacer(Modifier.height(10.dp)); Button(onClick = toChat) { Text("🤖 Ask SkillForge AI") }
        } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("Week" to "${Stats.pct(week)}%", "Month" to "${Stats.pct(month)}%", "🔥 Streak" to "${streak.first}d", "Done" to "$total").forEach { (l, v) ->
                Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(10.dp)) { Text(l, fontSize = 11.sp, color = Color.White.copy(0.5f)); Text(v, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) } } } } }
        item { SfCard { Text("Last 7 days", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); Bars(bars, labels) } }
        item { SfCard {
            Text("Today’s tasks", fontWeight = FontWeight.SemiBold)
            if (todayOcc.isEmpty()) Text("Nothing scheduled today.", color = Color.White.copy(0.5f))
            todayOcc.forEach { TaskRow(vm, it.g, it.k, it.done) }
        } }
        for ((title, f) in listOf("Weekly targets" to "WEEKLY", "Monthly targets" to "MONTHLY")) item { SfCard {
            Text(title, fontWeight = FontWeight.SemiBold)
            val l = goals.filter { it.frequency == f }
            if (l.isEmpty()) Text("None yet — add one in Goals.", color = Color.White.copy(0.5f))
            l.forEach { g -> val k = Recur.currentKey(g, t); TaskRow(vm, g, k, "${g.id}|$k" in m.done) }
        } }
        item { SfCard {
            Text("Upcoming reminders", fontWeight = FontWeight.SemiBold)
            val up = goals.mapNotNull { g -> Scheduler.nextTrigger(g)?.let { g to it } }.sortedBy { it.second }.take(4)
            if (up.isEmpty()) Text("No reminders set.", color = Color.White.copy(0.5f))
            up.forEach { (g, dt) -> Text("⏰ ${g.title} — ${dt.toLocalDate()} %02d:%02d".format(dt.hour, dt.minute), fontSize = 13.sp) }
        } }
    }
}

private fun meta(g: Goal) = "${g.category} • ${g.frequency.lowercase()} • ${PRI[g.priority.coerceIn(0, 2)]} • ${g.estMinutes}m" +
    (g.reminderMin?.let { " • ⏰ %02d:%02d".format(it / 60, it % 60) } ?: "") + (g.deadlineDay?.let { " • due ${LocalDate.ofEpochDay(it)}" } ?: "")

@Composable fun GoalsScreen(vm: MainVm) {
    val goals by vm.goals.collectAsState(); val comps by vm.comps.collectAsState(); val open by vm.openGoal.collectAsState()
    val m = Marks.of(comps); val t = LocalDate.now(); val ctx = LocalContext.current
    var edit by remember { mutableStateOf<Goal?>(null) }; var creating by remember { mutableStateOf(false) }; var del by remember { mutableStateOf<Goal?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Goals", style = MaterialTheme.typography.headlineMedium); Button(onClick = { creating = true }) { Text("＋ New") } } }
        if (goals.isEmpty()) item { Text("No goals yet. Tap “＋ New” or ask the AI chat to create one.", color = Color.White.copy(0.5f)) }
        items(goals, key = { it.id }) { g ->
            val k = Recur.currentKey(g, t); val done = "${g.id}|$k" in m.done
            SfCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(g.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); if (done) Text("✅") }
                if (g.description.isNotBlank()) Text(g.description, fontSize = 13.sp, color = Color.White.copy(0.6f))
                Text(meta(g), fontSize = 12.sp, color = Color.White.copy(0.5f))
                Row {
                    TextButton({ vm.setDone(g, k, !done) }) { Text(if (done) "Reopen" else "Complete") }
                    TextButton({ edit = g }) { Text("Edit") }
                    TextButton({ android.app.DatePickerDialog(ctx, { _, y, mo, d -> vm.move(g, LocalDate.of(y, mo + 1, d)) }, t.year, t.monthValue - 1, t.dayOfMonth).show() }) { Text("Move") }
                    TextButton({ del = g }) { Text("Delete") }
                }
            }
        }
    }
    val og = goals.find { it.id == open }
    if (og != null) GoalDialog(og, { vm.openGoal.value = null }) { vm.save(it); vm.openGoal.value = null }
    if (creating) GoalDialog(null, { creating = false }) { vm.save(it); creating = false }
    edit?.let { g -> GoalDialog(g, { edit = null }) { vm.save(it); edit = null } }
    del?.let { g -> AlertDialog(onDismissRequest = { del = null }, title = { Text("Delete goal?") },
        text = { Text("“${g.title}” and its history will be removed.") },
        confirmButton = { TextButton({ vm.delete(g); del = null }) { Text("Delete") } },
        dismissButton = { TextButton({ del = null }) { Text("Cancel") } }) }
}

@Composable fun GoalDialog(init: Goal?, onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val ctx = LocalContext.current; val today = LocalDate.now()
    var title by remember { mutableStateOf(init?.title ?: "") }; var desc by remember { mutableStateOf(init?.description ?: "") }
    var cat by remember { mutableStateOf(init?.category ?: CATS[0]) }; var pri by remember { mutableIntStateOf(init?.priority ?: 1) }
    var freq by remember { mutableStateOf(init?.frequency ?: "DAILY") }; var days by remember { mutableIntStateOf(init?.customDays?.takeIf { it != 0 } ?: 31) }
    var est by remember { mutableStateOf((init?.estMinutes ?: 30).toString()) }; var rem by remember { mutableStateOf(init?.reminderMin) }
    var start by remember { mutableLongStateOf(init?.startDay ?: today.toEpochDay()) }; var dl by remember { mutableStateOf(init?.deadlineDay) }
    fun datePick(cur: Long, set: (Long) -> Unit) { val d = LocalDate.ofEpochDay(cur); android.app.DatePickerDialog(ctx, { _, y, mo, dd -> set(LocalDate.of(y, mo + 1, dd).toEpochDay()) }, d.year, d.monthValue - 1, d.dayOfMonth).show() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (init == null) "New goal" else "Edit goal") },
        confirmButton = { TextButton({
            if (title.isNotBlank()) onSave((init ?: Goal(title = "", startDay = start)).copy(title = title.trim(), description = desc.trim(),
                category = cat.trim().ifBlank { "Personal Development" }, priority = pri, startDay = start, deadlineDay = dl, frequency = freq,
                customDays = if (freq == "CUSTOM") days else 0, estMinutes = est.toIntOrNull() ?: 30, reminderMin = rem))
        }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Text("Category", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (CATS + listOf(cat)).distinct().forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c, fontSize = 12.sp) }) } }
            OutlinedTextField(cat, { cat = it }, label = { Text("…or type a custom category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Priority", fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { PRI.forEachIndexed { i, p -> FilterChip(pri == i, { pri = i }, { Text(p) }) } }
            Text("Frequency", fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FREQ.forEach { f -> FilterChip(freq == f, { freq = f }, { Text(f.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            if (freq == "CUSTOM") Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                "MTWTFSS".forEachIndexed { i, ch -> FilterChip(((days shr i) and 1) == 1, { days = days xor (1 shl i) }, { Text("$ch") }) } }
            OutlinedTextField(est, { est = it.filter(Char::isDigit) }, label = { Text("Estimated minutes") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ android.app.TimePickerDialog(ctx, { _, h, mi -> rem = h * 60 + mi }, (rem ?: 480) / 60, (rem ?: 480) % 60, true).show() }) {
                    Text(rem?.let { "⏰ %02d:%02d".format(it / 60, it % 60) } ?: "Set reminder") }
                if (rem != null) TextButton({ rem = null }) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ datePick(start) { start = it } }) { Text("Start ${LocalDate.ofEpochDay(start)}") }
                OutlinedButton({ datePick(dl ?: start) { dl = it } }) { Text(dl?.let { "Due ${LocalDate.ofEpochDay(it)}" } ?: "Deadline") }
                if (dl != null) TextButton({ dl = null }) { Text("✕") }
            }
        } })
}

private fun describe(tool: String, a: JSONObject, goals: List<Goal>): String {
    fun gt() = goals.find { it.id == a.optLong("goal_id") }?.title ?: "goal #${a.optLong("goal_id")}"
    return when (tool) {
        "create_goal" -> "➕ Create “${a.optString("title")}” — ${a.optString("frequency", "once")}" +
            (a.optString("reminder_time").takeIf { it.isNotBlank() }?.let { " at $it" } ?: "") + " • ${a.optInt("est_minutes", 30)} min" +
            (a.optString("deadline").takeIf { it.isNotBlank() }?.let { " • due $it" } ?: "")
        "update_goal" -> "✏️ Edit “${gt()}”: " + a.keys().asSequence().filter { it != "goal_id" }.joinToString { "$it=${a.opt(it)}" }
        "complete_goal" -> "✅ Mark “${gt()}” as done"
        "reschedule_goal" -> "📅 Move “${gt()}” to ${a.optString("new_date")}"
        "delete_goal" -> "🗑️ Delete “${gt()}” (cannot be undone)"
        else -> tool
    }
}

@Composable fun ChatScreen(vm: MainVm) {
    val msgs by vm.chat.collectAsState(); val busy by vm.busy.collectAsState(); val goals by vm.goals.collectAsState()
    var input by remember { mutableStateOf("") }; val ls = rememberLazyListState()
    LaunchedEffect(msgs.size, busy) { val n = ls.layoutInfo.totalItemsCount; if (n > 0) ls.animateScrollToItem(n - 1) }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f), state = ls, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (msgs.isEmpty()) item { Column {
                Text("SkillForge AI", style = MaterialTheme.typography.headlineMedium)
                Text("Your study planner and coach. It reads your real goals and can create or change them — always after you confirm.", color = Color.White.copy(0.6f))
                Spacer(Modifier.height(8.dp))
                listOf("Show my pending weekly goals", "Create a daily goal to practice C++ for 30 minutes at 7 PM", "Review my progress this week", "What should I learn today?").forEach {
                    AssistChip({ vm.send(it) }, { Text(it, fontSize = 12.sp) }); Spacer(Modifier.height(4.dp)) }
            } }
            items(msgs, key = { it.id }) { m ->
                val mine = m.role == "user"
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    if (m.text.isNotBlank()) Box(Modifier.widthIn(max = 320.dp).background(
                        when (m.role) { "user" -> MaterialTheme.colorScheme.primary.copy(0.35f); "error" -> Color(0x33EF4444); else -> MaterialTheme.colorScheme.surfaceVariant }, RoundedCornerShape(16.dp)).padding(12.dp)) { Text(m.text) }
                    m.proposals?.let { js ->
                        val arr = JSONArray(js)
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i); val state = o.optString("state")
                            Spacer(Modifier.height(6.dp))
                            Card(Modifier.widthIn(max = 320.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(12.dp)) {
                                    if (state == "pending") {
                                        Text(describe(o.getString("tool"), o.getJSONObject("args"), goals), fontSize = 14.sp)
                                        Row { TextButton({ vm.resolve(m, i, true) }) { Text("Confirm") }; TextButton({ vm.resolve(m, i, false) }) { Text("Dismiss") } }
                                    } else Text(if (state == "dismissed") "Dismissed" else o.optString("result"), fontSize = 13.sp, color = Color.White.copy(0.7f))
                                }
                            }
                        }
                    }
                }
            }
            if (busy) item { Text("SkillForge is thinking…", color = Color.White.copy(0.5f)) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ask or plan something…") }, maxLines = 4)
            Button(onClick = { if (input.isNotBlank() && !busy) { vm.send(input.trim()); input = "" } }, enabled = !busy) { Text("Send") }
        }
    }
}

@Composable fun SettingsScreen(vm: MainVm) {
    val ctx = LocalContext.current; val mem by vm.memory.collectAsState()
    var url by remember { mutableStateOf(Prefs.url(ctx)) }; var token by remember { mutableStateOf(Prefs.token(ctx)) }
    var tick by remember { mutableIntStateOf(0) }; var memK by remember { mutableStateOf("") }; var memV by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notifOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactOk = remember(tick) { Scheduler.canExact(ctx) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item { SfCard {
            Text("Notifications", fontWeight = FontWeight.SemiBold)
            Text("Permission: " + if (notifOk) "granted ✅" else "not granted ❌", fontSize = 13.sp)
            if (!notifOk) Button({ perm.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
            if (Build.VERSION.SDK_INT >= 31) {
                Text("Exact alarms: " + if (exactOk) "allowed ✅" else "not allowed — reminders may be a few minutes late", fontSize = 13.sp)
                if (!exactOk) Button({ ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))); tick++ }) { Text("Allow exact alarms") }
            }
            OutlinedButton({ Notifier.test(ctx) }) { Text("Send test notification") }
        } }
        item { SfCard {
            Text("AI backend", fontWeight = FontWeight.SemiBold)
            Text("The AI provider key stays on your server. Enter your own backend URL and the app token you chose.", fontSize = 12.sp, color = Color.White.copy(0.6f))
            OutlinedTextField(url, { url = it; Prefs.set(ctx, url, token) }, label = { Text("Backend URL (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(token, { token = it; Prefs.set(ctx, url, token) }, label = { Text("App token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } }
        item { SfCard {
            Text("Chatbot memory (${mem.size})", fontWeight = FontWeight.SemiBold)
            Text("Only non-sensitive planning context is saved. Edit or delete anything.", fontSize = 12.sp, color = Color.White.copy(0.6f))
            mem.forEach { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(it.k, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary); Text(it.v, fontSize = 13.sp) }
                TextButton({ memK = it.k; memV = it.v }) { Text("Edit") }; TextButton({ vm.delMemory(it.k) }) { Text("✕") } } }
            OutlinedTextField(memK, { memK = it }, label = { Text("Key (e.g. study_schedule)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(memV, { memV = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth())
            Button({ vm.putMemory(memK, memV); memK = ""; memV = "" }) { Text("Save memory item") }
        } }
        item { SfCard {
            Text("Privacy & data", fontWeight = FontWeight.SemiBold)
            Text("Each chat message sends your goal list, stats and saved memory to your backend and the AI provider so answers use real data.", fontSize = 12.sp, color = Color.White.copy(0.6f))
            Row {
                TextButton({ if (confirm == "chat") { vm.clearChat(); confirm = "" } else confirm = "chat" }) { Text(if (confirm == "chat") "Tap again to confirm" else "Clear chat history") }
                TextButton({ if (confirm == "mem") { vm.clearMemory(); confirm = "" } else confirm = "mem" }) { Text(if (confirm == "mem") "Tap again to confirm" else "Clear memory") }
            }
        } }
    }
}
