package jp.co.jex.fukuroncamera;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            // Summary に現在値を設定
            setListPreferenceSummary("size");
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(key.equals("size")) {
                setListPreferenceSummary(key);
            }
        }

        /**
         * EditTextPreference の Summary 現在値を設定します。
         * 現在値が設定されていない場合、R.string.default_summary を設定します。
         * @param key EditTextPreference の key
         */
        private void setEditTextPreferenceSummary(String key) {
            EditTextPreference editText = (EditTextPreference) getPreferenceScreen().findPreference(key);
            if (editText.getText() == null || editText.getText().equals("")) {
                editText.setText(null);
                editText.setSummary(getString(R.string.default_summary));
            } else {
                editText.setSummary(editText.getText());
            }
        }

        /**
         * ListPreference の Summary 現在値を設定します。
         * 現在値が設定されていない場合、R.string.default_summary を設定します。
         * @param key ListPreference の key
         */
        private void setListPreferenceSummary(String key) {
            ListPreference list = (ListPreference) getPreferenceScreen().findPreference(key);
            if (list.getEntry() == null) {
                list.setSummary(getString(R.string.default_summary));
            } else {
                list.setSummary(list.getEntry());
            }
        }
    }
}