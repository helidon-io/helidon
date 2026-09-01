/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.langchain4j.providers.cohere;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class MockSocksProxy implements AutoCloseable {
    private static final int SOCKS_VERSION = 5;

    private final ServerSocket serverSocket;
    private final CompletableFuture<CapturedRequest> request = new CompletableFuture<>();
    private final Thread serverThread;
    private volatile Socket activeSocket;

    MockSocksProxy() throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        serverThread = Thread.ofPlatform()
                .daemon()
                .name("mock-socks-proxy")
                .start(this::serve);
    }

    Proxy proxy() {
        return new Proxy(Proxy.Type.SOCKS,
                         new InetSocketAddress(InetAddress.getLoopbackAddress(), serverSocket.getLocalPort()));
    }

    CapturedRequest capturedRequest() throws Exception {
        return request.get(5, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException {
        var socket = activeSocket;
        if (socket != null) {
            socket.close();
        }
        serverSocket.close();
        try {
            serverThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void serve() {
        try (var socket = serverSocket.accept();
                var input = new DataInputStream(socket.getInputStream());
                var output = socket.getOutputStream()) {
            activeSocket = socket;
            socket.setSoTimeout(5000);
            expect(input, SOCKS_VERSION, "greeting version");
            int methodCount = input.readUnsignedByte();
            readFully(input, methodCount);
            output.write(new byte[] {SOCKS_VERSION, 0});
            output.flush();

            expect(input, SOCKS_VERSION, "request version");
            expect(input, 1, "CONNECT command");
            expect(input, 0, "reserved byte");
            String targetHost = readHost(input);
            int targetPort = input.readUnsignedShort();
            output.write(new byte[] {SOCKS_VERSION, 0, 0, 1, 0, 0, 0, 0, 0, 0});
            output.flush();

            String httpRequest = readHttpRequest(input);

            byte[] body = ("{\"results\":[{\"index\":0,\"relevance_score\":0.875}],"
                    + "\"meta\":{\"billed_units\":{\"search_units\":7}}}").getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
            request.complete(new CapturedRequest(targetHost, targetPort, httpRequest));
        } catch (Exception e) {
            request.completeExceptionally(e);
        } finally {
            activeSocket = null;
        }
    }

    private static String readHost(DataInputStream input) throws IOException {
        return switch (input.readUnsignedByte()) {
        case 1 -> InetAddress.getByAddress(readFully(input, 4)).getHostAddress();
        case 3 -> new String(readFully(input, input.readUnsignedByte()), StandardCharsets.US_ASCII);
        case 4 -> InetAddress.getByAddress(readFully(input, 16)).getHostAddress();
        default -> throw new IOException("Unsupported SOCKS address type");
        };
    }

    private static String readHttpRequest(DataInputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            if (bytes.size() == 65_536) {
                throw new IOException("HTTP request headers are too large");
            }
            int next = input.read();
            if (next < 0) {
                throw new IOException("Unexpected end of HTTP request");
            }
            bytes.write(next);
            matched = switch (matched) {
            case 0 -> next == '\r' ? 1 : 0;
            case 1 -> next == '\n' ? 2 : 0;
            case 2 -> next == '\r' ? 3 : 0;
            case 3 -> next == '\n' ? 4 : 0;
            default -> matched;
            };
        }

        String headers = bytes.toString(StandardCharsets.ISO_8859_1);
        int contentLength = headers.lines()
                .filter(line -> line.regionMatches(true, 0, "Content-Length:", 0, "Content-Length:".length()))
                .mapToInt(line -> Integer.parseInt(line.substring("Content-Length:".length()).trim()))
                .findFirst()
                .orElse(0);
        bytes.write(readFully(input, contentLength));
        return bytes.toString(StandardCharsets.ISO_8859_1);
    }

    private static byte[] readFully(DataInputStream input, int length) throws IOException {
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    private static void expect(DataInputStream input, int expected, String description) throws IOException {
        int actual = input.readUnsignedByte();
        if (actual != expected) {
            throw new IOException("Unexpected " + description + ": " + actual);
        }
    }

    record CapturedRequest(String targetHost, int targetPort, String httpRequest) {
    }
}
