package android.support.v7.preference;

/**
 * Created by Carlos on 5/9/2018.
 */

public class Preference {
    public interface OnPreferenceChangeListener {
        boolean onPreferenceChange(Preference preference, Object newValue);
    }

    public void setKey(String key) {
        throw new RuntimeException("Stub!");
    }

    public String getKey() {
        throw new RuntimeException("Stub!");
    }
}
