package io.github.mpstudios56.cifra.http;

import org.json.JSONObject;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetching a page from the network, in the three shapes the app asks for.
 * <p>
 * Used only to look up exchange rates, which is the one thing Cifra goes out to
 * the network for on its own account.
 */
public class HttpClientWrapper {

    private final OkHttpClient client;

    public HttpClientWrapper(OkHttpClient client) {
        this.client = client;
    }

    /** The answer read as figures, for a service that speaks JSON. */
    public JSONObject getAsJson(String url) throws Exception {
        return new JSONObject(getAsString(url));
    }

    /** The answer as it came, whatever it says. */
    public String getAsString(String url) throws Exception {
        return get(url).body().string();
    }

    /**
     * The answer, but only when the far end said it went well.
     * <p>
     * A refusal often carries a message worth showing, so it is raised rather
     * than swallowed - a rate that failed to arrive must not look like a rate
     * of zero.
     */
    public String getAsStringIfOk(String url) throws Exception {
        Response response = get(url);
        String said = response.body().string();
        if (!response.isSuccessful()) {
            throw new RuntimeException(said);
        }
        return said;
    }

    protected Response get(String url) throws IOException {
        return client.newCall(new Request.Builder().url(url).build()).execute();
    }
}
