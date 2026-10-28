package com.example.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.BridgeConfig
import com.example.data.model.TodayItem
import com.example.data.model.TodayPage
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val page: TodayPage? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val config: BridgeConfig? = null,      // the image loader needs the agent's address and token
    val answered: Set<Long> = emptySet()   // question cards answered from here (the reply is in the chat)
)

class TodayViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application, AppDatabase.getDatabase(application))
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    /** The latest edition (the agent builds a new one first when it's due); [refresh] asks for one now. */
    fun load(refresh: Boolean = false, editionId: Long? = null) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val config = repository.getActiveConfig()
                val page = repository.getToday(editionId, refresh)
                _state.update { it.copy(page = page, loading = false, config = config) }
                // Shown now: the agent learns which cards were seen (a saved one stays saved)
                page.items.filter { it.state == "new" }.forEach { launch { repository.reactToday(it.id, "seen") } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't reach the PC") }
            }
        }
    }

    /** save | unsave | less | more | dismiss | answered: shown at once, then sent to the agent. */
    fun react(item: TodayItem, action: String) {
        val newState = when (action) {
            "save" -> "saved"; "unsave" -> "seen"; "less" -> "less"; "more" -> "more"
            "dismiss" -> "dismissed"; "answered" -> "answered"; else -> item.state
        }
        _state.update { s ->
            val page = s.page ?: return@update s
            val items = if (newState == "dismissed") page.items.filterNot { it.id == item.id }
                        else page.items.map { if (it.id == item.id) it.copy(state = newState) else it }
            s.copy(page = page.copy(items = items),
                   answered = if (action == "answered") s.answered + item.id else s.answered)
        }
        viewModelScope.launch { repository.reactToday(item.id, action) }
    }
}
