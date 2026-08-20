package io.github.mpstudios56.cifra.preference;

/**
 * The two services a copy can be sent to, each with its own settings and its
 * own two buttons.
 * <p>
 * The same screen as its parent, showing the other half of it: the groups that
 * belong to the phone itself are hidden here, and the two services are hidden
 * there.
 */
public class OnlineBackupPreferencesFragment extends BackupPreferencesFragment {

    @Override
    protected boolean isOnline() {
        return true;
    }
}
