package club.chums.wear;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.ServerSocket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class HubConnectionTest {
    @Test public void preservesBinaryAttachmentBytes() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            Thread reply = new Thread(() -> {
                try (var socket = server.accept()) {
                    var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    while (!reader.readLine().isEmpty()) { }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().write(new byte[]{0, -1, -128, 42});
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
            });
            reply.start();
            HubConnection hub = new HubConnection("http://127.0.0.1:" + server.getLocalPort(), "fixture", true);
            assertArrayEquals(new byte[]{0, -1, -128, 42}, hub.requestBytes("/v1/artifact/abcdef012345/full", null));
            assertThrows(java.io.IOException.class, () -> hub.requestBytes("/v1/../config", null));
            reply.join(1000);
        }
    }

    @Test public void validatesAddressesAndTokens() {
        assertEquals("https://example.com", new HubConnection(" https://example.com/ ", "secret", false).base);
        for (String url : new String[]{"http://example.com", "https://user:pass@example.com", "https://example.com/path", "https://example.com?token=x", "https://example.com/#x", "garbage"}) {
            assertThrows(IllegalArgumentException.class, () -> new HubConnection(url, "secret", true));
        }
        assertThrows(IllegalArgumentException.class, () -> new HubConnection("http://127.0.0.1:7718", "secret", false));
        assertThrows(IllegalArgumentException.class, () -> new HubConnection("https://example.com", "\nsecret", false));
        assertThrows(IllegalArgumentException.class, () -> new HubConnection("https://example.com", " ", false));
    }
    @Test public void sendsAuthenticatedJsonAndRefusesRedirects() throws Exception {
        ServerSocket server = new ServerSocket(0, 3, java.net.InetAddress.getByName("127.0.0.1"));
        AtomicReference<String> body = new AtomicReference<>(), auth = new AtomicReference<>();
        Thread responder = new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    try (var socket = server.accept()) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String request = in.readLine(), line;
                        int length = 0;
                        while (!(line = in.readLine()).isEmpty()) {
                            if (line.startsWith("Authorization: ")) auth.set(line.substring(15));
                            if (line.toLowerCase().startsWith("content-length: ")) length = Integer.parseInt(line.substring(16));
                        }
                        if (length > 0) {
                            StringBuilder text = new StringBuilder();
                            for (int c = 0; c < length; c++) text.append((char) in.read());
                            body.set(text.toString());
                        }
                        String status = request.contains("/redirect") ? "302 Found" : request.contains("/denied") ? "401 Unauthorized" : "200 OK";
                        String response = "{\"kind\":\"order\"}";
                        socket.getOutputStream().write(("HTTP/1.1 " + status + "\r\nLocation: /v1/say\r\nContent-Length: " + response.length() + "\r\nConnection: close\r\n\r\n" + response).getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (java.io.IOException ignored) { }
        });
        responder.start();
        try {
            HubConnection hub = new HubConnection("http://127.0.0.1:" + server.getLocalPort(), "test-secret", true);
            assertEquals("{\"kind\":\"order\"}", hub.request("/v1/say", "{\"text\":\"hello\"}"));
            assertEquals("Bearer test-secret", auth.get());
            assertEquals("{\"text\":\"hello\"}", body.get());
            assertThrows(java.io.IOException.class, () -> hub.request("/v1/redirect", null));
            assertEquals("Hub refused the watch token", assertThrows(java.io.IOException.class, () -> hub.request("/v1/denied", null)).getMessage());
        } finally { server.close(); responder.join(1000); }
    }
}
