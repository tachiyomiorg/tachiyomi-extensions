package android.support.v7.preference;

public class Preference {

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

    public void setSummary(CharSequence summary) {
        throw new RuntimeException("Stub!");
    }

    public void setEnabled(boolean enabled)  {
        throw new RuntimeException("Stub!");
    }

    public boolean isEnabled() {
        throw new RuntimeException("Stub!");
    }

    public String getKey() {
        throw new RuntimeException("Stub!");
    }

    public void setKey(String key) {
        throw new RuntimeException("Stub!");
    }

    public void setDefaultValue(Object defaultValue) {
        throw new RuntimeException("Stub!");
    }

    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

}
