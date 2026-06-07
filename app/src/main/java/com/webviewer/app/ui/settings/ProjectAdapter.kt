package com.webviewer.app.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.webviewer.app.databinding.ItemProjectBinding
import com.webviewer.app.model.Project

class ProjectAdapter(
    private val onEdit: (Project) -> Unit,
    private val onDelete: (Project) -> Unit,
    private val onDuplicate: (Project) -> Unit,
    private val onActivate: (Project) -> Unit
) : ListAdapter<Project, ProjectAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val b: ItemProjectBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(project: Project) {
            b.tvProjectIcon.text = project.iconEmoji
            b.tvProjectName.text = project.name
            b.tvProjectUrl.text = project.url
            b.tvActive.text = if (project.isActive) "● ATTIVO" else ""
            b.btnEdit.setOnClickListener { onEdit(project) }
            b.btnDelete.setOnClickListener { onDelete(project) }
            b.btnDuplicate.setOnClickListener { onDuplicate(project) }
            b.btnActivate.setOnClickListener { onActivate(project) }
            b.btnActivate.text = if (project.isActive) "Attivo" else "Attiva"
            b.btnActivate.isEnabled = !project.isActive
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(a: Project, b: Project) = a.id == b.id
        override fun areContentsTheSame(a: Project, b: Project) = a == b
    }
}
