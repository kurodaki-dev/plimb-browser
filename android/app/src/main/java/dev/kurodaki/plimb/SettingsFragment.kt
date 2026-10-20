package dev.kurodaki.plimb

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

/** Preferences du navigateur. */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_root, rootKey)

        findPreference<ListPreference>(Prefs.KEY_SEARCH_ENGINE)?.apply {
            entries = Prefs.ENGINES.map { Prefs.ENGINE_LABELS[it.first] }.toTypedArray()
            entryValues = Prefs.ENGINES.map { it.first }.toTypedArray()
            summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                val value = preference.value ?: Prefs.ENGINES.first().first
                Prefs.ENGINE_LABELS[value] ?: value
            }
        }

        findPreference<SwitchPreferenceCompat>(Prefs.KEY_DARK)?.setOnPreferenceChangeListener { _, newValue ->
            AppCompatDelegate.setDefaultNightMode(
                if (newValue as Boolean) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
            requireActivity().recreate()
            true
        }

        findPreference<Preference>("open_extensions")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ExtensionsActivity::class.java))
            true
        }
    }
}
