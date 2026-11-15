package club.chums.wear;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class HubConnection {
    final String base;
    private final String token;

    HubConnection(String address, String token, boolean debug) {
        URI uri;
        try { uri = URI.create(address.trim()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enter a valid HTTPS hub address"); }
        boolean loopback = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!("https".equals(uri.getScheme()) || (debug && loopback && "http".equals(uri.getScheme())))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Use an HTTPS hub address without a path, query or login");
        }
        if (token.trim().isEmpty() || token.contains("\n") || token.contains("\r"))
            throw new IllegalArgumentException("Enter the hub's watch token");
        this.base = address.trim().replaceAll("/+$", "");
        this.token = token.trim();
    }

    String request(String path, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(base + path).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(28000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (var out = connection.getOutputStream()) { out.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException(code == 401 ? "Hub refused the watch token" : "Hub returned HTTP " + code);
            try (var in = connection.getInputStream()) {
                java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (result.size() + count > 16 * 1024 * 1024) throw new IOException("Hub response too large");
                    result.write(buffer, 0, count);
                }
                return result.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
}
