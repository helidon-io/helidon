/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import io.helidon.common.http.Http;
import io.helidon.webserver.utils.SocketHttpClient;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * Test support for HTTP 1.1 pipelining.
 */
public class HttpPipelineTest {
    private static final Logger LOGGER = Logger.getLogger(HttpPipelineTest.class.getName());

    private static X509TrustManager TRUST_MANAGER = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    };

    private static WebServer webServer;
    private static AtomicInteger counter = new AtomicInteger(0);
    private static ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
    private static OkHttpClient client;

    private static class LoggingInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Interceptor.Chain chain) throws IOException {
            Request request = chain.request();

            long t1 = System.nanoTime();
            System.out.println(String.format("Sending request %s on %s%n%s",
                    request.url(), chain.connection(), request.headers()));

            Response response = chain.proceed(request);

            long t2 = System.nanoTime();
            System.out.println(String.format("Received response for %s in %.1fms%nProtocol is %s%n%s",
                    response.request().url(), (t2 - t1) / 1e6d, response.protocol(), response.headers()));

            return response;
        }
    }

    @BeforeAll
    public static void startServer() throws Exception {
        startServer(0);
    }

    @AfterAll
    public static void close() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private static void startServer(int port) throws Exception {
        webServer = WebServer.builder()
                .experimental(ExperimentalConfiguration.builder().http2(
                        Http2Configuration.builder().enable(true).build()).build())
                .port(port)
                .routing(Routing.builder()
                        .put("/", (req, res) -> {
                            counter.set(0);
                            res.send();
                        })
                        .get("/", (req, res) -> {
                            int n = counter.getAndIncrement();
                            int delay = (n % 2 == 0) ? 1000 : 0;    // alternate delay 1 second and no delay
                            executor.schedule(() -> res.status(Http.Status.OK_200).send("Response " + n + "\n"),
                                    delay, TimeUnit.MILLISECONDS);
                        })
                        .build())
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE));
        client = clientBuilder.build();

        LOGGER.info("Started server at: https://localhost:" + webServer.port());
    }

    /**
     * Pipelines request_0 and request_1 and makes sure responses are returned in the
     * correct order. Note that the server will delay the response for request_0 to
     * make sure they are properly synchronized.
     *
     * @throws Exception If there are connection problems.
     */
    @Test
    public void testPipelining() throws Exception {
        try (SocketHttpClient s = new SocketHttpClient(webServer)) {
            s.request(Http.Method.PUT, "/");        // reset server
            s.request(Http.Method.GET, "/");        // request_0
            s.request(Http.Method.GET, "/");        // request_1
            String put = s.receive();
            assertThat(put, notNullValue());
            String get0 = s.receive();
            assertThat(get0, containsString("Response 0"));
            String get1 = s.receive();
            assertThat(get1, containsString("Response 1"));
        }
    }

    /**
     * Same as previous test but using HTTP/2 and OkHttp as async client.
     */
    @Test
    public void testPipeliningHttp2() throws Exception {
        MediaType mt = MediaType.get("text/plain");
        URL url = new URL("http://localhost:" + webServer.port() + "/");
        Request put = new Request.Builder().url(url).put(RequestBody.create(mt, "")).build();
        client.newCall(put).execute();

        Request get = new Request.Builder().url(url).build();
        CompletableFuture<?> cf0 = new CompletableFuture<>();
        CompletableFuture<?> cf1 = new CompletableFuture<>();

        client.newCall(get).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cf0.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response r0) throws IOException {
                LOGGER.info("Received r0");
                if (cf1.isDone()) {
                    LOGGER.info("Expected r0 before r1");
                    cf0.completeExceptionally(new RuntimeException("Expected r0 before r1"));
                } else {
                    assertThat(r0.body().string(), containsString("Response 0"));
                    cf0.complete(null);
                }
            }
        });

        client.newCall(get).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                cf1.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response r1) throws IOException {
                LOGGER.info("Received r1");
                try {
                    cf0.get(100L, TimeUnit.MILLISECONDS);   // give r0 callback a chance
                    assertThat(r1.body().string(), containsString("Response 1"));
                    cf1.complete(null);
                } catch (InterruptedException | ExecutionException | TimeoutException  e) {
                    LOGGER.info("Expected r0 before r1");
                    cf1.completeExceptionally(e);
                }
            }
        });
        CompletableFuture.allOf(cf0, cf1).get(2000L, TimeUnit.MILLISECONDS);
    }
}
