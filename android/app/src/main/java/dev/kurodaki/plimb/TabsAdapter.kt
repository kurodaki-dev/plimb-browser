package dev.kurodaki.plimb

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Adaptateur de la liste d onglets. */
class TabsAdapter(
    private val items: List<PlimbTab>,
    private val activeIndex: Int,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : RecyclerView.Adapter<TabsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tab_title)
        val url: TextView = view.findViewById(R.id.tab_url)
        val close: ImageButton = view.findViewById(R.id.tab_close)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = items[position]
        val context = holder.itemView.context
        holder.title.text = tab.title.ifBlank { context.getString(R.string.new_tab) }
        holder.url.text = tab.url
        holder.itemView.isSelected = position == activeIndex
        holder.itemView.setOnClickListener { onSelect(position) }
        holder.close.setOnClickListener { onClose(position) }
    }
}
