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

package io.helidon.integrations.vault.auths.token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.http.Http;
import io.helidon.config.Config;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.common.rest.RestRequest;
import io.helidon.integrations.vault.Vault;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class VaultTokenRedirectTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String VAULT_TOKEN_HEADER = "X-Vault-Token";
    private static final String TOKEN = "token-value";
    private static final AtomicReference<String> VAULT_TOKEN = new AtomicReference<>();
    private static final AtomicReference<String> CAPTURED_TOKEN = new AtomicReference<>();
    private static final AtomicReference<Throwable> SERVER_ERROR = new AtomicReference<>();

    private static ExecutorService executor;
    private static ServerSocket vaultServer;
    private static ServerSocket redirectTargetServer;

    @BeforeAll
    static void beforeAll() throws IOException {
        executor = Executors.newFixedThreadPool(2);
        redirectTargetServer = new ServerSocket(0);
        executor.submit(() -> {
            try (Socket socket = redirectTargetServer.accept()) {
                CAPTURED_TOKEN.set(readHeader(socket, VAULT_TOKEN_HEADER));
                writeResponse(socket.getOutputStream(),
                              "HTTP/1.1 200 OK\r\n"
                                      + "Content-Length: 0\r\n"
                                      + "Connection: close\r\n"
                                      + "\r\n");
            } catch (Throwable t) {
                SERVER_ERROR.compareAndSet(null, t);
            }
        });

        vaultServer = new ServerSocket(0);
        executor.submit(() -> {
            try (Socket socket = vaultServer.accept()) {
                VAULT_TOKEN.set(readHeader(socket, VAULT_TOKEN_HEADER));
                writeResponse(socket.getOutputStream(),
                              "HTTP/1.1 302 Found\r\n"
                                      + "Location: http://localhost:" + redirectTargetServer.getLocalPort() + "/capture\r\n"
                                      + "Content-Length: 0\r\n"
                                      + "Connection: close\r\n"
                                      + "\r\n");
            } catch (Throwable t) {
                SERVER_ERROR.compareAndSet(null, t);
            }
        });
    }

    @AfterAll
    static void afterAll() throws IOException {
        if (vaultServer != null) {
            vaultServer.close();
        }
        if (redirectTargetServer != null) {
            redirectTargetServer.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @BeforeEach
    void resetCapturedToken() {
        VAULT_TOKEN.set(null);
        CAPTURED_TOKEN.set(null);
    }

    @Test
    void crossOriginRedirectStripsVaultToken() {
        Vault.Builder vaultBuilder = Vault.builder()
                .address("http://127.0.0.1:" + vaultServer.getLocalPort());
        RestApi restApi = TokenVaultAuth.builder()
                .token(TOKEN)
                .build()
                .authenticate(Config.empty(), vaultBuilder)
                .orElseThrow();

        TestResponse response = restApi.delete("/sys/policy/test",
                                               RestRequest.builder(),
                                               new TestResponse.Builder())
                .await(TIMEOUT);

        assertThat(response.status(), is(Http.Status.OK_200));
        if (SERVER_ERROR.get() != null) {
            throw new AssertionError("Test server failed", SERVER_ERROR.get());
        }
        assertThat(VAULT_TOKEN.get(), is(TOKEN));
        assertThat(CAPTURED_TOKEN.get(), is(nullValue()));
    }

    private static String readHeader(Socket socket, String headerName) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String headerPrefix = headerName + ":";
        String headerValue = null;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            if (line.regionMatches(true, 0, headerPrefix, 0, headerPrefix.length())) {
                headerValue = line.substring(headerPrefix.length()).trim();
            }
        }
        return headerValue;
    }

    private static void writeResponse(OutputStream outputStream, String response) throws IOException {
        outputStream.write(response.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
    }

    private static final class TestResponse extends ApiResponse {
        private TestResponse(Builder builder) {
            super(builder);
        }

        private static final class Builder extends ApiResponse.Builder<Builder, TestResponse> {
            @Override
            public TestResponse build() {
                return new TestResponse(this);
            }
        }
    }
}
