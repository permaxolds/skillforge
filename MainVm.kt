package com.skillforge.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skillforge.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class MainVm(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val dao = repo.dao
    val goals = dao.goals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val comps = dao.completions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chat = dao.chat().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memory = dao.memory().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val busy = MutableStateFlow(false)
    val openGoal = MutableStateFlow<Long?>(null)

    fun save(g: Goal) = viewModelScope.launch { repo.saveGoal(g) }
    fun delete(g: Goal) = viewModelScope.launch { repo.deleteGoal(g.id) }
    fun setDone(g: Goal, k: String, d: Boolean) = viewModelScope.launch { repo.setDone(g, k, d) }
    fun move(g: Goal, day: LocalDate) = viewModelScope.launch { repo.move(g, day) }
    fun clearChat() = viewModelScope.launch { dao.clearChat() }
    fun clearMemory() = viewModelScope.launch { dao.clearMem() }
    fun delMemory(k: String) = viewModelScope.launch { dao.delMem(k) }
    fun putMemory(k: String, v: String) = viewModelScope.launch { if (k.isNotBlank()) dao.putMem(Mem(k.trim(), v.trim())) }

    fun send(text: String) = viewModelScope.launch {
        val c = getApplication<Application>()
        dao.addChat(ChatMsg(role = "user", text = text)); busy.value = true
        try {
            val url = Prefs.url(c)
            if (url.isBlank()) error("Backend URL not set — open Settings and enter your backend URL and token.")
            val hist = dao.lastChat(20).reversed().filter { it.role == "user" || it.role == "assistant" }.map { it.role to it.text }
            val r = Api.chat(url, Prefs.token(c), hist, repo.contextJson())
            val props = JSONArray(); val arr = r.optJSONArray("proposals") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val o = JSONObject().put("tool", p.getString("tool")).put("args", p.getJSONObject("args"))
                if (p.getString("tool") == "save_memory") o.put("state", "done").put("result", repo.apply("save_memory", p.getJSONObject("args")))
                else o.put("state", "pending")
                props.put(o)
            }
            dao.addChat(ChatMsg(role = "assistant", text = r.optString("reply"), proposals = if (props.length() > 0) props.toString() else null))
        } catch (e: Exception) {
            dao.addChat(ChatMsg(role = "error", text = "⚠️ " + (e.message ?: "Request failed")))
        } finally { busy.value = false }
    }

    /** Apply (after user confirmation) or dismiss one proposal inside an assistant message. */
    fun resolve(m: ChatMsg, i: Int, accept: Boolean) = viewModelScope.launch {
        val arr = JSONArray(m.proposals); val o = arr.getJSONObject(i)
        if (accept) {
            val res = try { repo.apply(o.getString("tool"), o.getJSONObject("args")) } catch (e: Exception) { "⚠️ " + e.message }
            o.put("result", res).put("state", "done")
        } else o.put("state", "dismissed")
        dao.updateChat(m.copy(proposals = arr.toString()))
    }
}
