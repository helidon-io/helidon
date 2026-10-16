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

package io.helidon.webclient.benchmark.jmh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.logging.common.LogConfig;
import io.helidon.webclient.api.WebClientServiceResponse;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.http1.Http1Config;
import io.helidon.webserver.http1.Http1ConnectionSelector;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * HTTP/1 response framing and completion on a reused connection.
 * Without a client service, unadvertised trailers use the JDK client so server comparisons
 * remain valid against versions of the Helidon client that cannot safely reuse such responses.
 * Service modes always use the Helidon client.
 */
public class Http1ResponseJmhBenchmark {
    private static final String HOST = "127.0.0.1";
    private static final String TRAILER_NAME = "x-benchmark";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Benchmark
    public byte[] response(NetworkState state) {
        return state.exchange();
    }

    public enum ClientService {
        NONE,
        WRAP,
        BUFFER,
        BUFFER_GZIP
    }

    public enum ResponseMode {
        FIXED_BUFFERED(false, false, false, false),
        FIXED_FLUSHED(false, true, false, false),
        CHUNKED_BUFFERED(true, false, false, false),
        CHUNKED_FLUSHED(true, true, false, false),
        ADVERTISED_BUFFERED(true, false, true, true),
        ADVERTISED_FLUSHED(true, true, true, true),
        UNADVERTISED_BUFFERED(true, false, true, false),
        UNADVERTISED_FLUSHED(true, true, true, false);

        private final boolean chunked;
        private final boolean flushed;
        private final boolean trailers;
        private final boolean advertised;

        ResponseMode(boolean chunked, boolean flushed, boolean trailers, boolean advertised) {
            this.chunked = chunked;
            this.flushed = flushed;
            this.trailers = trailers;
            this.advertised = advertised;
        }
    }

    @State(Scope.Benchmark)
    public static class NetworkState {
        @Param
        public ResponseMode responseMode;

        @Param({"1024"})
        public int payloadSize;

        @Param({"false", "true"})
        public boolean validateHeaders;

        @Param({"NONE"})
        public ClientService clientService;

        private byte[] payload;
        private byte[] wirePayload;
        private WebServer server;
        private Http1Client helidonClient;
        private HttpClient jdkClient;
        private HttpRequest jdkRequest;

        @Setup(Level.Trial)
        public void setup() {
            if (payloadSize < 2) {
                throw new IllegalArgumentException("The response payload must contain at least two bytes");
            }
            payload = new byte[payloadSize];
            Arrays.fill(payload, (byte) 'x');
            wirePayload = payload;
            if (clientService == ClientService.BUFFER_GZIP) {
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (GZIPOutputStream output = new GZIPOutputStream(compressed)) {
                    output.write(payload);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                wirePayload = compressed.toByteArray();
            }
            LogConfig.configureRuntime();
            try {
                server = WebServer.builder()
                        .host(HOST)
                        .writeBufferSize(Math.max(16 * 1024, payloadSize + 1))
                        .addConnectionSelector(Http1ConnectionSelector.builder()
                                                       .config(Http1Config.builder()
                                                                       .validateResponseHeaders(validateHeaders)
                                                                       .build())
                                                       .build())
                        .routing(routing -> routing.get("/", this::handle))
                        .build()
                        .start();

                URI uri = URI.create("http://" + HOST + ":" + server.port() + "/");
                if (clientService == ClientService.NONE && responseMode.trailers && !responseMode.advertised) {
                    jdkClient = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(TIMEOUT)
                            .build();
                    jdkRequest = HttpRequest.newBuilder(uri)
                            .timeout(TIMEOUT)
                            .header("TE", "trailers")
                            .GET()
                            .build();
                } else {
                    var builder = Http1Client.builder()
                            .baseUri(uri)
                            .readTimeout(TIMEOUT)
                            .shareConnectionCache(false)
                            .servicesDiscoverServices(false);
                    if (clientService != ClientService.NONE) {
                        builder.addService((chain, request) -> decorate(chain.proceed(request)));
                    }
                    if (clientService == ClientService.BUFFER_GZIP) {
                        builder.contentEncoding(ContentEncodingContext.builder().addContentEncoding(new GzipEncoding()));
                    }
                    helidonClient = builder.build();
                }

                // Check another exchange as well to catch unread framing on the reused connection.
                for (int i = 0; i < 2; i++) {
                    byte[] received = exchange(true);
                    if (!Arrays.equals(received, payload)) {
                        throw new IllegalStateException("Unexpected decoded response entity for " + responseMode
                                                                + " with client service " + clientService);
                    }
                }
            } catch (RuntimeException e) {
                tearDown();
                throw e;
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (helidonClient != null) {
                helidonClient.closeResource();
            }
            if (jdkClient != null) {
                jdkClient.close();
            }
            if (server != null) {
                server.stop();
            }
        }

        private byte[] exchange() {
            return exchange(false);
        }

        private byte[] exchange(boolean validateTrailers) {
            if (jdkClient != null) {
                try {
                    HttpResponse<byte[]> response = jdkClient.send(jdkRequest, HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != Status.OK_200.code()) {
                        throw new IllegalStateException("Unexpected response status: " + response.statusCode());
                    }
                    return response.body();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Benchmark request interrupted", e);
                }
            }
            var request = helidonClient.get();
            if (responseMode.trailers && !responseMode.advertised) {
                request.header(HeaderValues.TE_TRAILERS);
            }
            try (Http1ClientResponse response = request.request()) {
                if (response.status().code() != Status.OK_200.code()) {
                    throw new IllegalStateException("Unexpected response status: " + response.status());
                }
                byte[] received = response.entity().as(byte[].class);
                if (validateTrailers && responseMode.trailers
                        && !response.trailers().get(HeaderNames.create(TRAILER_NAME)).get().equals("complete")) {
                    throw new IllegalStateException("Unexpected response trailer for " + responseMode);
                }
                return received;
            }
        }

        private WebClientServiceResponse decorate(WebClientServiceResponse response) {
            InputStream input = response.inputStream().orElseThrow();
            if (clientService == ClientService.WRAP) {
                return WebClientServiceResponse.builder(response)
                        .inputStream(new FilterInputStream(input) { })
                        .build();
            }
            byte[] buffered;
            try {
                buffered = input.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            WritableHeaders<?> headers = WritableHeaders.create(response.headers());
            headers.remove(HeaderNames.TRANSFER_ENCODING);
            headers.remove(HeaderNames.CONTENT_ENCODING);
            headers.set(HeaderNames.CONTENT_LENGTH, buffered.length);
            // The client retains transport cleanup ownership when this replacement does not delegate close.
            return WebClientServiceResponse.builder(response)
                    .headers(ClientResponseHeaders.create(headers))
                    .inputStream(new ByteArrayInputStream(buffered))
                    .build();
        }

        private void handle(ServerRequest request, ServerResponse response) {
            if (responseMode.chunked) {
                response.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            } else {
                response.header(HeaderNames.CONTENT_LENGTH, Integer.toString(wirePayload.length));
            }
            if (clientService == ClientService.BUFFER_GZIP) {
                response.header(HeaderNames.CONTENT_ENCODING, "gzip");
            }
            if (responseMode.advertised) {
                response.header(HeaderNames.TRAILER, TRAILER_NAME);
            }
            if (responseMode.trailers) {
                response.beforeTrailers(trailers -> trailers.set(HeaderNames.create(TRAILER_NAME), "complete"));
            }
            try (OutputStream output = response.outputStream()) {
                if (responseMode.flushed) {
                    int firstLength = wirePayload.length / 2;
                    output.write(wirePayload, 0, firstLength);
                    output.flush();
                    output.write(wirePayload, firstLength, wirePayload.length - firstLength);
                } else {
                    output.write(wirePayload);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private record GzipEncoding() implements ContentEncoding {
        @Override
        public String name() {
            return "gzip";
        }

        @Override
        public String type() {
            return "gzip";
        }

        @Override
        public Set<String> ids() {
            return Set.of("gzip");
        }

        @Override
        public boolean supportsEncoding() {
            return false;
        }

        @Override
        public boolean supportsDecoding() {
            return true;
        }

        @Override
        public ContentDecoder decoder() {
            return network -> {
                try {
                    return new GZIPInputStream(network);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }

        @Override
        public ContentEncoder encoder() {
            return ContentEncoder.NO_OP;
        }
    }
}
