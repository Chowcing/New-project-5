package com.example.expense.ocr.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expense.ocr.service.LocalOcrProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class OcrHttpClientConfigTest {
    @Test
    void restClientDoesNotSendH2cUpgradeHeadersToLocalOcrService() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<String> rawRequest = executor.submit(() -> captureOneRequest(serverSocket));
            OcrProperties properties = new OcrProperties();
            properties.getLocal().setBaseUrl("http://127.0.0.1:" + serverSocket.getLocalPort());
            LocalOcrProvider provider = new LocalOcrProvider(properties, new OcrHttpClientConfig().restClientBuilder());
            MockMultipartFile image = new MockMultipartFile(
                    "image",
                    "receipt.png",
                    "image/png",
                    new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

            provider.recognize(image);

            String request = rawRequest.get(5, TimeUnit.SECONDS);
            assertThat(request).contains("name=\"image\"");
            assertThat(request).contains("filename=\"receipt.png\"");
            assertThat(request).doesNotContain("Upgrade: h2c");
            assertThat(request).doesNotContain("HTTP2-Settings");
            assertThat(request).doesNotContain("Connection: Upgrade");
            executor.shutdownNow();
        }
    }

    private String captureOneRequest(ServerSocket serverSocket) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(2000);
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            readHeaders(socket, raw);
            String headers = raw.toString(StandardCharsets.ISO_8859_1);
            String normalizedHeaders = headers.toLowerCase(Locale.ROOT);
            if (normalizedHeaders.contains("transfer-encoding: chunked")) {
                readChunkedBody(socket, raw);
            } else {
                readFixedLengthBody(socket, raw, contentLength(headers));
            }
            byte[] responseBody = "{\"text\":\"ok\",\"provider\":\"diagnostic\"}".getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + responseBody.length + "\r\n"
                    + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            socket.getOutputStream().write(responseBody);
            socket.getOutputStream().flush();
            return raw.toString(StandardCharsets.ISO_8859_1);
        }
    }

    private void readHeaders(Socket socket, ByteArrayOutputStream raw) throws IOException {
        byte[] buffer = new byte[1];
        while (!raw.toString(StandardCharsets.ISO_8859_1).contains("\r\n\r\n")) {
            int read = socket.getInputStream().read(buffer);
            if (read < 0) {
                return;
            }
            raw.write(buffer, 0, read);
        }
    }

    private void readChunkedBody(Socket socket, ByteArrayOutputStream raw) throws IOException {
        byte[] buffer = new byte[1024];
        while (!raw.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n0\r\n\r\n")) {
            int read = socket.getInputStream().read(buffer);
            if (read < 0) {
                return;
            }
            raw.write(buffer, 0, read);
        }
    }

    private void readFixedLengthBody(Socket socket, ByteArrayOutputStream raw, int contentLength) throws IOException {
        if (contentLength <= 0) {
            return;
        }
        String headers = raw.toString(StandardCharsets.ISO_8859_1);
        int alreadyRead = raw.size() - headers.indexOf("\r\n\r\n") - 4;
        int remaining = contentLength - alreadyRead;
        byte[] buffer = new byte[1024];
        while (remaining > 0) {
            int read = socket.getInputStream().read(buffer, 0, Math.min(buffer.length, remaining));
            if (read < 0) {
                return;
            }
            raw.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private int contentLength(String headers) {
        Matcher matcher = Pattern.compile("(?im)^Content-Length:\\s*(\\d+)\\s*$").matcher(headers);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }
}
