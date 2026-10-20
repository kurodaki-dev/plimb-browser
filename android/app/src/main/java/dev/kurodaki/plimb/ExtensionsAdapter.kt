package dev.kurodaki.plimb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial

/** Adaptateur listant les extensions avec leur etat. */
class ExtensionsAdapter(
    private val onToggle: (Extension, Boolean) -> Unit,
    private val onDelete: (Extension) -> Unit
) : ListAdapter<Extension, ExtensionsAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.ext_title)
        val meta: TextView = view.findViewById(R.id.ext_meta)
        val description: TextView = view.findViewById(R.id.ext_description)
        val toggle: SwitchMaterial = view.findViewById(R.id.ext_switch)
        val delete: ImageButton = view.findViewById(R.id.ext_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_extension, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val extension = getItem(position)
        val context = holder.itemView.context
        holder.title.text = extension.name
        holder.meta.text = listOfNotNull(
            extension.version.takeIf { it.isNotBlank() },
            extension.author.takeIf { it.isNotBlank() },
            if (extension.bundled) context.getString(R.string.ext_bundled)
            else context.getString(R.string.ext_user)
        ).joinToString(" · ")
        holder.description.text = extension.description
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = extension.isEnabled(context)
        holder.toggle.setOnCheckedChangeListener { _, value ->
            onToggle(extension, value)
        }
        holder.delete.visibility = if (extension.bundled) View.GONE else View.VISIBLE
        holder.delete.setOnClickListener { onDelete(extension) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Extension>() {
            override fun areItemsTheSame(oldItem: Extension, newItem: Extension): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Extension, newItem: Extension): Boolean =
                oldItem == newItem
        }
    }
}
