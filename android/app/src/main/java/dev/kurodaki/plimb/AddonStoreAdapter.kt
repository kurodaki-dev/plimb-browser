package dev.kurodaki.plimb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/** Adaptateur du catalogue d add-ons Mozilla. */
class AddonStoreAdapter(
    private val onInstall: (AddonListing) -> Unit
) : ListAdapter<AddonListing, AddonStoreAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.addon_name)
        val summary: TextView = view.findViewById(R.id.addon_summary)
        val meta: TextView = view.findViewById(R.id.addon_meta)
        val install: MaterialButton = view.findViewById(R.id.addon_install)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_addon, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val listing = getItem(position)
        val context = holder.itemView.context
        holder.name.text = listing.name
        holder.summary.text = listing.summary
        val users = if (listing.users > 0) {
            context.getString(R.string.addon_users, listing.users / 1000)
        } else {
            ""
        }
        holder.meta.text = users
        holder.install.setOnClickListener { onInstall(listing) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AddonListing>() {
            override fun areItemsTheSame(oldItem: AddonListing, newItem: AddonListing): Boolean =
                oldItem.downloadUrl == newItem.downloadUrl

            override fun areContentsTheSame(oldItem: AddonListing, newItem: AddonListing): Boolean =
                oldItem == newItem
        }
    }
}
