package fr.chatcmds;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Minimal HTTP helper. Never puts a key or a URL in an error message. */
final class Http {
    static final class ApiException extends Exception {
        ApiException(String message) {
            super(message);
        }
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Http() {}

    /**
     * @param service     "Hypixel" or "Last.fm", used in error messages
     * @param headerName  optional header such as "API-Key" (null for none)
     */
    static JsonElement getJson(String url, String headerName, String headerValue, String service) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "chatcmds-mod/2.0");
        if (headerName != null && headerValue != null && !headerValue.isEmpty()) {
            b.header(headerName, headerValue);
        }
        HttpResponse<String> res = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        if (code == 403) {
            throw new ApiException(service.equals("Hypixel")
                    ? "Hypixel rejected the API key (expired or invalid). Use /cmd key <new key>"
                    : service + " rejected the API key or the account is private.");
        }
        if (code == 429) throw new ApiException(service + " rate limit reached, try again in a few seconds.");
        if (code != 200) throw new ApiException(service + " answered HTTP " + code + ".");
        return JsonParser.parseString(res.body());
    }
}
