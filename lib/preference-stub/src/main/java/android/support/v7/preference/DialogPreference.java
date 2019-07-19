package android.support.v7.preference;

/**
 * Created by Carlos on 5/9/2018.
 */

public abstract class DialogPreference extends Preference {
    public CharSequence getDialogTitle() {
        throw new RuntimeException("Stub!");
    }

    public void setDialogTitle(CharSequence dialogTitle) {
        throw new RuntimeException("Stub!");
    }

    public CharSequence getDialogMessage() {
        throw new RuntimeException("Stub!");
    }

    public void setDialogMessage(CharSequence dialogMessage) {
        throw new RuntimeException("Stub!");
    }
}
