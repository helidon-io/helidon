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

package io.helidon.webserver.benchmark.jmh;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import io.helidon.http.Method;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http1.Http1Route;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Thread)
public class AbsoluteFormRequestTargetJmhBenchmark {
    private static final String SERVER_HOST = "127.0.0.1";
    private static final int SOCKET_READ_TIMEOUT_MILLIS = 5_000;
    private static final byte[] RESPONSE_BYTES = "OK".getBytes(StandardCharsets.US_ASCII);

    private final byte[] responseBuffer = new byte[1024];

    @Param
    private Workload workload;

    private WebServer server;
    private Socket originFormSocket;
    private Socket absoluteFormSocket;
    private InputStream originFormInput;
    private InputStream absoluteFormInput;
    private OutputStream originFormOutput;
    private OutputStream absoluteFormOutput;
    private byte[] originFormRequest;
    private byte[] absoluteFormRequest;

    @Setup
    public void setup() throws IOException {
        LogConfig.configureRuntime();
        Http1Route route;
        if (workload == Workload.AUTHORITY) {
            route = Http1Route.route(Method.GET, "/plaintext", (req, res) -> {
                String authority = req.authority();
                res.send(authority.equals(req.authority()) ? "OK" : "Unexpected authority");
            });
        } else {
            route = Http1Route.route(Method.GET, "/plaintext", (req, res) -> {
                String authority = req.authority();
                var requestedUri = req.requestedUri();
                boolean expectedAuthority = authority.equals(requestedUri.authority());
                boolean expectedScheme = "http".equals(requestedUri.scheme());
                res.send(expectedAuthority && expectedScheme ? "OK" : "Unexpected requested URI");
            });
        }
        server = WebServer.builder()
                .featuresDiscoverServices(false)
                .connectionOptions(builder -> builder
                        .readTimeout(Duration.ZERO)
                        .connectTimeout(Duration.ZERO))
                .host(SERVER_HOST)
                .routing(router -> router.route(route))
                .build()
                .start();

        int serverPort = server.port();
        originFormSocket = socket(serverPort);
        absoluteFormSocket = socket(serverPort);
        originFormInput = originFormSocket.getInputStream();
        absoluteFormInput = absoluteFormSocket.getInputStream();
        originFormOutput = originFormSocket.getOutputStream();
        absoluteFormOutput = absoluteFormSocket.getOutputStream();
        originFormRequest = request("/plaintext", serverPort);
        absoluteFormRequest = request("http://service.example:" + serverPort + "/plaintext", serverPort);
    }

    @TearDown
    public void tearDown() throws IOException {
        originFormSocket.close();
        absoluteFormSocket.close();
        server.stop();
    }

    @Benchmark
    public int originForm() throws IOException {
        return request(originFormOutput, originFormInput, originFormRequest);
    }

    @Benchmark
    public int absoluteForm() throws IOException {
        return request(absoluteFormOutput, absoluteFormInput, absoluteFormRequest);
    }

    private static Socket socket(int serverPort) throws IOException {
        Socket socket = new Socket(SERVER_HOST, serverPort);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(SOCKET_READ_TIMEOUT_MILLIS);
        return socket;
    }

    private static byte[] request(String requestTarget, int serverPort) {
        return ("GET " + requestTarget + " HTTP/1.1\r\n"
                + "Host: conflict.example:" + serverPort + "\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    private static int bodyStart(byte[] responseBuffer, int bytesRead) {
        for (int i = 3; i < bytesRead; i++) {
            if (responseBuffer[i - 3] == '\r'
                    && responseBuffer[i - 2] == '\n'
                    && responseBuffer[i - 1] == '\r'
                    && responseBuffer[i] == '\n') {
                return i + 1;
            }
        }
        return -1;
    }

    private static boolean bodyMatches(byte[] responseBuffer, int bodyStart) {
        for (int i = 0; i < RESPONSE_BYTES.length; i++) {
            if (responseBuffer[bodyStart + i] != RESPONSE_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    private int request(OutputStream output, InputStream input, byte[] request) throws IOException {
        output.write(request);
        output.flush();
        return readResponse(input);
    }

    private int readResponse(InputStream input) throws IOException {
        int bytesRead = 0;
        int bodyStart = -1;
        while (bytesRead < responseBuffer.length) {
            int read = input.read(responseBuffer, bytesRead, responseBuffer.length - bytesRead);
            if (read == -1) {
                throw new EOFException("Response ended before complete body was received");
            }
            bytesRead += read;
            if (bodyStart == -1) {
                bodyStart = bodyStart(responseBuffer, bytesRead);
            }
            if (bodyStart != -1 && bytesRead >= bodyStart + RESPONSE_BYTES.length) {
                if (!bodyMatches(responseBuffer, bodyStart)) {
                    throw new IOException("Unexpected response body");
                }
                return bytesRead;
            }
        }
        throw new IOException("Response exceeded benchmark buffer before complete body was received");
    }

    public enum Workload {
        AUTHORITY,
        REQUESTED_URI
    }
}
