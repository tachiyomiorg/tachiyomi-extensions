package eu.kanade.tachiyomi.source;

public interface ConfigurableSource {

    void setupPreferenceScreen(android.support.v7.preference.PreferenceScreen screen);

    void setupPreferenceScreen(androidx.preference.PreferenceScreen screen);

}
