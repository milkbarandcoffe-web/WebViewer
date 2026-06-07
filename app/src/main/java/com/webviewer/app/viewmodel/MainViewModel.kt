package com.webviewer.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webviewer.app.data.db.AppDatabase
import com.webviewer.app.data.repository.BackupRepository
import com.webviewer.app.data.repository.ProjectRepository
import com.webviewer.app.data.repository.SettingsRepository
import com.webviewer.app.model.AppSettings
import com.webviewer.app.model.Project
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val projectRepo = ProjectRepository(db.projectDao())
    val settingsRepo = SettingsRepository(application)
    val backupRepo = BackupRepository(application)

    // Tutti i progetti — Flow live da Room
    val projects: StateFlow<List<Project>> = projectRepo.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings correnti
    val settings: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    // URL corrente nella WebView
    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl

    // Stato caricamento WebView
    sealed class LoadState {
        object Idle : LoadState()
        object Loading : LoadState()
        data class Error(val message: String, val url: String) : LoadState()
        object NoNetwork : LoadState()
        object Success : LoadState()
    }
    private val _loadState = MutableStateFlow<LoadState>(LoadState.Idle)
    val loadState: StateFlow<LoadState> = _loadState

    // Toast one-shot
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

    // MacroDroid: stato bolla (prima/seconda pressione lunga)
    private val _bollaActive = MutableStateFlow(false)
    val bollaActive: StateFlow<Boolean> = _bollaActive

    /** Carica il progetto attivo all'avvio */
    fun loadActiveProject() {
        viewModelScope.launch {
            val active = projectRepo.getActiveProject()
            if (active != null) {
                _currentUrl.value = active.url
                projectRepo.updateLastUsed(active.id)
            } else {
                // Nessun progetto attivo: usa ultima URL dalla sessione
                val lastUrl = settings.value.lastSessionUrl
                if (lastUrl.isNotBlank()) _currentUrl.value = lastUrl
            }
        }
    }

    fun setUrl(url: String) {
        _currentUrl.value = url
        viewModelScope.launch { settingsRepo.saveLastUrl(url) }
    }

    fun setLoadState(state: LoadState) { _loadState.value = state }

    fun toast(msg: String) { viewModelScope.launch { _toastMessage.emit(msg) } }

    fun toggleBolla() { _bollaActive.value = !_bollaActive.value }

    // ---- Operazioni Progetto ----

    fun addProject(name: String, url: String, icon: String = "🌐") {
        viewModelScope.launch {
            val id = projectRepo.insert(Project(name = name, url = url, iconEmoji = icon))
            toast("Progetto \"$name\" aggiunto")
            // Se è il primo progetto, attivalo automaticamente
            if (projects.value.isEmpty()) projectRepo.setActive(id)
        }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch {
            projectRepo.update(project)
            toast("Progetto aggiornato")
            // Se è il progetto attivo, aggiorna URL corrente
            if (project.isActive) _currentUrl.value = project.url
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            projectRepo.delete(project)
            toast("Progetto eliminato")
        }
    }

    fun duplicateProject(project: Project) {
        viewModelScope.launch {
            projectRepo.duplicate(project)
            toast("Progetto duplicato")
        }
    }

    fun setActiveProject(project: Project) {
        viewModelScope.launch {
            projectRepo.setActive(project.id)
            settingsRepo.saveActiveProject(project.id)
            _currentUrl.value = project.url
            toast("Progetto attivo: ${project.name}")
        }
    }

    // ---- Settings ----

    fun saveSettings(settings: AppSettings) {
        viewModelScope.launch { settingsRepo.saveSettings(settings) }
    }

    // ---- Backup ----

    fun exportBackup() {
        viewModelScope.launch {
            val result = backupRepo.export(projects.value)
            result.fold(
                onSuccess = { toast("Backup esportato") },
                onFailure = { toast("Errore export: ${it.message}") }
            )
        }
    }

    fun importBackup() {
        viewModelScope.launch {
            val result = backupRepo.import()
            result.fold(
                onSuccess = { list ->
                    projectRepo.deleteAll()
                    list.forEach { projectRepo.insert(it) }
                    toast("${list.size} progetti importati")
                },
                onFailure = { toast("Errore import: ${it.message}") }
            )
        }
    }

    // ---- Sistema ----

    fun clearCache(context: android.content.Context) {
        viewModelScope.launch {
            android.webkit.WebStorage.getInstance().deleteAllData()
            context.cacheDir.deleteRecursively()
            toast("Cache svuotata")
        }
    }
}
