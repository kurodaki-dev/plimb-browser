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

/** Adaptateur de la liste d extensions installees. */
class ExtensionsAdapter(
    private val onToggle: (AddonEntry, Boolean) -> Unit,
    private val onRemove: (AddonEntry) -> Unit
) : ListAdapter<AddonEntry, ExtensionsAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.ext_title)
        val meta: TextView = view.findViewById(R.id.ext_meta)
        val description: TextView = view.findViewById(R.id.ext_description)
        val toggle: SwitchMaterial = view.findViewById(R.id.ext_switch)
        val remove: ImageButton = view.findViewById(R.id.ext_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_extension, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = getItem(position)
        val context = holder.itemView.context
        holder.title.text = entry.name
        holder.meta.text = listOfNotNull(
            entry.version.takeIf { it.isNotBlank() },
            if (entry.builtIn) context.getString(R.string.ext_bundled)
            else context.getString(R.string.ext_user)
        ).joinToString(" · ")
        holder.description.text = entry.description
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = entry.enabled
        holder.toggle.setOnCheckedChangeListener { _, value -> onToggle(entry, value) }
        holder.remove.visibility = if (entry.builtIn) View.GONE else View.VISIBLE
        holder.remove.setOnClickListener { onRemove(entry) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AddonEntry>() {
            override fun areItemsTheSame(oldItem: AddonEntry, newItem: AddonEntry): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: AddonEntry, newItem: AddonEntry): Boolean =
                oldItem == newItem
        }
    }
}
