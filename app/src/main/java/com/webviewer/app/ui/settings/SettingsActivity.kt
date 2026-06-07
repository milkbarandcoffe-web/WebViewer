package com.webviewer.app.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.webviewer.app.BuildConfig
import com.webviewer.app.R
import com.webviewer.app.databinding.ActivitySettingsBinding
import com.webviewer.app.model.AppSettings
import com.webviewer.app.model.Project
import com.webviewer.app.util.MacroDroidHelper
import com.webviewer.app.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val vm: MainViewModel by viewModels()
    private lateinit var projectAdapter: ProjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupProjectList()
        setupMacroDroidSection()
        setupSystemSection()
        setupInfoSection()
        observeProjects()
        observeSettings()

        binding.btnBack.setOnClickListener { finish() }
    }

    // ---- Progetti ----

    private fun setupProjectList() {
        projectAdapter = ProjectAdapter(
            onEdit = { showEditProjectDialog(it) },
            onDelete = { confirmDelete(it) },
            onDuplicate = { vm.duplicateProject(it) },
            onActivate = { vm.setActiveProject(it) }
        )
        binding.rvProjects.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = projectAdapter
        }
        binding.btnAddProject.setOnClickListener { showAddProjectDialog() }
    }

    private fun showAddProjectDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_project, null)
        val etName = view.findViewById<EditText>(R.id.etProjectName)
        val etUrl = view.findViewById<EditText>(R.id.etProjectUrl)
        val etIcon = view.findViewById<EditText>(R.id.etProjectIcon)

        AlertDialog.Builder(this)
            .setTitle("Nuovo progetto")
            .setView(view)
            .setPositiveButton("Aggiungi") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                val icon = etIcon.text.toString().trim().ifBlank { "🌐" }
                if (name.isNotBlank() && url.isNotBlank()) {
                    vm.addProject(name, url, icon)
                } else {
                    Toast.makeText(this, "Nome e URL obbligatori", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showEditProjectDialog(project: Project) {
        val view = layoutInflater.inflate(R.layout.dialog_project, null)
        val etName = view.findViewById<EditText>(R.id.etProjectName)
        val etUrl = view.findViewById<EditText>(R.id.etProjectUrl)
        val etIcon = view.findViewById<EditText>(R.id.etProjectIcon)

        etName.setText(project.name)
        etUrl.setText(project.url)
        etIcon.setText(project.iconEmoji)

        AlertDialog.Builder(this)
            .setTitle("Modifica progetto")
            .setView(view)
            .setPositiveButton("Salva") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                val icon = etIcon.text.toString().trim().ifBlank { "🌐" }
                if (name.isNotBlank() && url.isNotBlank()) {
                    vm.updateProject(project.copy(name = name, url = url, iconEmoji = icon))
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun confirmDelete(project: Project) {
        AlertDialog.Builder(this)
            .setTitle("Elimina")
            .setMessage("Eliminare \"${project.name}\"?")
            .setPositiveButton("Elimina") { _, _ -> vm.deleteProject(project) }
            .setNegativeButton("Annulla", null)
            .show()
    }

    // ---- MacroDroid ----

    private fun setupMacroDroidSection() {
        binding.btnTestMacroStart.setOnClickListener {
            val pkg = binding.etMacroPackage.text.toString().trim()
            val name = binding.etMacroName.text.toString().trim()
            val ok = MacroDroidHelper.triggerMacro(this, pkg, name)
            Toast.makeText(this, if (ok) "▶ Macro avviata" else "Errore / MacroDroid non trovato", Toast.LENGTH_SHORT).show()
        }
        binding.btnTestMacroStop.setOnClickListener {
            val pkg = binding.etMacroPackage.text.toString().trim()
            val name = binding.etMacroName.text.toString().trim()
            MacroDroidHelper.stopMacro(this, pkg, name)
            Toast.makeText(this, "Stop inviato", Toast.LENGTH_SHORT).show()
        }
        binding.btnSaveMacro.setOnClickListener {
            val pkg = binding.etMacroPackage.text.toString().trim()
            val name = binding.etMacroName.text.toString().trim()
            val current = vm.settings.value
            vm.saveSettings(current.copy(macroDroidPackage = pkg, macroDroidMacroName = name))
            Toast.makeText(this, "Impostazioni MacroDroid salvate", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Sistema ----

    private fun setupSystemSection() {
        binding.btnClearCache.setOnClickListener {
            vm.clearCache(this)
        }
        binding.btnResetCookies.setOnClickListener {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            Toast.makeText(this, "Cookie rimossi", Toast.LENGTH_SHORT).show()
        }
        binding.btnExportBackup.setOnClickListener {
            vm.exportBackup()
        }
        binding.btnImportBackup.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Importa backup")
                .setMessage("I progetti attuali verranno sostituiti. Continuare?")
                .setPositiveButton("Importa") { _, _ -> vm.importBackup() }
                .setNegativeButton("Annulla", null)
                .show()
        }
        binding.btnResetApp.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset dati app")
                .setMessage("Elimina TUTTI i progetti e le impostazioni. Impossibile annullare.")
                .setPositiveButton("Reset") { _, _ ->
                    lifecycleScope.launch {
                        vm.projectRepo.deleteAll()
                        Toast.makeText(this@SettingsActivity, "Dati resettati", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    // ---- Info ----

    private fun setupInfoSection() {
        binding.tvVersionApp.text = "Versione app: ${BuildConfig.VERSION_NAME}"
        binding.tvVersionAndroid.text = "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        binding.tvDevice.text = "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}"

        binding.btnDiagnostics.setOnClickListener {
            val sb = StringBuilder()
            sb.appendLine("=== DIAGNOSTICA ===")
            sb.appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            sb.appendLine("Android: ${Build.VERSION.RELEASE}")
            sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            val settings = vm.settings.value
            sb.appendLine("MacroDroid pkg: ${settings.macroDroidPackage}")
            sb.appendLine("MacroDroid installato: ${MacroDroidHelper.isInstalled(this, settings.macroDroidPackage)}")
            sb.appendLine("Progetti: ${vm.projects.value.size}")
            sb.appendLine("URL corrente: ${vm.currentUrl.value}")

            AlertDialog.Builder(this)
                .setTitle("Diagnostica")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ---- Observer ----

    private fun observeProjects() {
        lifecycleScope.launch {
            vm.projects.collectLatest { list ->
                projectAdapter.submitList(list)
                binding.tvNoProjects.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            vm.toastMessage.collectLatest { msg ->
                Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            vm.settings.collectLatest { s ->
                if (binding.etMacroPackage.text.toString() != s.macroDroidPackage) {
                    binding.etMacroPackage.setText(s.macroDroidPackage)
                }
                if (binding.etMacroName.text.toString() != s.macroDroidMacroName) {
                    binding.etMacroName.setText(s.macroDroidMacroName)
                }
            }
        }
    }
}
