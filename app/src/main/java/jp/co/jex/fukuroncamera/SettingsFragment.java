package jp.co.jex.fukuroncamera;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.support.annotation.Nullable;
import android.widget.ListAdapter;

public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        updateSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            updateSummary();
        }
    };

    private void updateSummary() {
        ListAdapter adapter = getPreferenceScreen().getRootAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item instanceof EditTextPreference) {
                EditTextPreference editTextPreference = (EditTextPreference) item;
                editTextPreference.setSummary(editTextPreference.getText() == null ? "" : editTextPreference.getText());
            }
        }
    }
}
