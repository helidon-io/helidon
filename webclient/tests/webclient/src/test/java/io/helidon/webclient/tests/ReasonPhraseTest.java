/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.http.HeaderNames;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static java.lang.System.Logger.Level.DEBUG;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Reason phrase is optional, but space behind status is mandatory.
 * <pre>{@code
 *  Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
 *  Reason-Phrase  = *<TEXT, excluding CR, LF>
 *  CR = <US-ASCII CR, carriage return (13)>
 *  LF  = <US-ASCII LF, linefeed (10)>
 *  SP  = <US-ASCII SP, space (32)>
 * }</pre>
 */
class ReasonPhraseTest {

    private static final System.Logger LOGGER = System.getLogger(ReasonPhraseTest.class.getName());
    private static final String CUSTOM_STATUS_LINE = "Custom-Status-Line";
    private static final Pattern CUSTOM_STATUS_LINE_HEADER_PATTERN = Pattern.compile("Custom-Status-Line: ([^\r]*)");
    private static final String CRLF = "\r\n";
    private static ServerSocket socket;
    private static WebClient client;
    private static ExecutorService executor;

    @BeforeAll
    static void beforeAll() throws IOException {
        LogConfig.configureRuntime();
        socket = new ServerSocket();
        socket.bind(new InetSocketAddress("localhost", 0));

        client = WebClient.builder()
                .keepAlive(true)
                .readTimeout(Duration.ofSeconds(1))
                .baseUri("http://localhost:" + socket.getLocalPort())
                .build();

        executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(ReasonPhraseTest::startMockServer);
    }

    @AfterAll
    static void afterAll() throws InterruptedException {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executor.shutdownNow();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "HTTP/1.1 204 No content",
            "HTTP/1.1 204 Custom reason",
            "HTTP/1.1 204 "
    })
    void allowedStatusLines(String statusLine) {
        HttpClientResponse res = client
                .delete("/test")
                .header(HeaderNames.create(CUSTOM_STATUS_LINE), statusLine)
                .request();

        assertFalse(res.entity().hasEntity());
        IllegalStateException e = assertThrowsExactly(IllegalStateException.class,
                                                      () -> res.as(String.class));
        MatcherAssert.assertThat(e.getMessage(), is("No entity"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "HTTP/1.1 204"
    })
    void badStatusLines(String statusLine) {
        var e = assertThrowsExactly(IllegalStateException.class,
                                    () -> client
                                            .delete("/test")
                                            .header(HeaderNames.create(CUSTOM_STATUS_LINE), statusLine)
                                            .request());

        MatcherAssert.assertThat(e.getMessage(),
                                 startsWith("HTTP Response did not contain HTTP status line. Line: HTTP/1.0 or HTTP/1.1"));
    }

    private static void startMockServer() {
        while (!socket.isClosed()) {
            try (var s = socket.accept();
                    var os = s.getOutputStream();
                    var is = s.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ByteBuffer bb = ByteBuffer.allocate(1024);
                int bytesRecevied;
                while ((bytesRecevied = is.read(bb.array())) != -1) {
                    baos.write(bb.array(), 0, bytesRecevied);
                    if (is.available() == 0) {
                        break;
                    }
                }

                String requestContent = baos.toString();

                // parse out custom status header value
                String customStatusLine = Arrays.stream(requestContent.split(CRLF))
                        .map(CUSTOM_STATUS_LINE_HEADER_PATTERN::matcher)
                        .filter(Matcher::matches)
                        .findFirst()
                        .map(m -> m.group(1))
                        .orElseThrow();

                writeLine(customStatusLine, os);
                writeLine("Content-Type: text/plain;charset=UTF-8", os);
                writeLine("Keep-Alive: timeout=20", os);
                writeLine("Connection: keep-alive", os);
                writeLine("", os);
                os.flush();
            } catch (Exception e) {
                LOGGER.log(DEBUG, "Error in mock server.", e);
                break;
            }
        }
    }

    private static void writeLine(String line, OutputStream os) throws IOException {
        os.write(line.getBytes(StandardCharsets.UTF_8));
        os.write(CRLF.getBytes(StandardCharsets.UTF_8));
    }

}
