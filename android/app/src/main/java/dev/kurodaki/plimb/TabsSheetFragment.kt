package dev.kurodaki.plimb

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/** Feuille inferieure listant les onglets ouverts (selection / fermeture). */
class TabsSheetFragment(
    private val items: List<PlimbTab>,
    private val activeIndex: Int,
    private val onSelect: (Int) -> Unit,
    private val onClose: (Int) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_tabs_sheet, container, false)
        val recycler: RecyclerView = view.findViewById(R.id.tabs_recycler)
        val newTab: MaterialButton = view.findViewById(R.id.sheet_new_tab)

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = TabsAdapter(
            items = items,
            activeIndex = activeIndex,
            onSelect = { index ->
                onSelect(index)
                dismiss()
            },
            onClose = { index ->
                onClose(index)
                dismiss()
            }
        )

        newTab.setOnClickListener {
            (activity as? MainActivity)?.openTab(MainActivity.HOME_PAGE)
            dismiss()
        }
        return view
    }
}
