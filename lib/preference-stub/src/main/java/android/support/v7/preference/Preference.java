package android.support.v7.preference;

/**
 * Created by Carlos on 5/9/2018.
 */

public class Preference {
    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public void setOnPreferenceChangeListener(OnPreferenceChangeListener onPreferenceChangeListener) {
        throw new RuntimeException("Stub!");
    }

    public void setOnPreferenceClickListener(OnPreferenceClickListener onPreferenceClickListener) {
        throw new RuntimeException("Stub!");
    }

    public CharSequence getTitle() {
        throw new RuntimeException("Stub!");
    }

    public void setTitle(CharSequence title) {
        throw new RuntimeException("Stub!");
    }

    public CharSequence getSummary() {
        throw new RuntimeException("Stub!");
    }

    public void setKey(String key) {
        throw new RuntimeException("Stub!");
    }

    public String getKey() {
        throw new RuntimeException("Stub!");
    }

    public void setSummary(CharSequence summary) {
        throw new RuntimeException("Stub!");
    }

    public void setDefaultValue(Object defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }
}
