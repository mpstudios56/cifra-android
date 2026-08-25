package io.github.mpstudios56.cifra.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Whether there is any point trying to reach the network.
 * <p>
 * Asked before fetching rates or sending a copy to a service: failing straight
 * away with a word about it is kinder than a request that hangs until it gives
 * up on its own.
 * <p>
 * A connection still being made counts as one. By the time the request is
 * actually sent it will usually be up, and refusing on a connection that is a
 * moment from ready would send somebody looking for a fault that is not there.
 */
public class NetworkUtils {

    private NetworkUtils() {
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return false;
        }
        NetworkInfo network = connectivity.getActiveNetworkInfo();
        return network != null && network.isAvailable() && network.isConnectedOrConnecting();
    }
}
