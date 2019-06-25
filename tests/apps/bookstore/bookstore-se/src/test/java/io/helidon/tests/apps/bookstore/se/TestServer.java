/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.apps.bookstore.se;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.webserver.WebServer;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import org.junit.jupiter.api.Assertions;

/**
 * Utility class to start the Helidon server with options.
 */
class TestServer {

    static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

    private static X509TrustManager TRUST_MANAGER = new X509TrustManager() {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
        }
    };

    static WebServer start(boolean ssl, boolean http2) throws Exception {
        WebServer webServer = Main.startServer(ssl, http2);

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }

        return webServer;
    }

    static void stop(WebServer webServer) throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    static String getBookAsJson() throws Exception {
        try (InputStream is = TestServer.class.getClassLoader().getResourceAsStream("book.json")) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
            throw new RuntimeException("Unable to find resource book.json");
        }
    }

    static SSLContext setupSSLTrust() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] { TRUST_MANAGER }, new SecureRandom());
        return sslContext;
    }

    static Request.Builder newRequestBuilder(WebServer webServer, String path, boolean ssl) throws Exception {
        URL url = new URL((ssl ? "https" : "http") + "://localhost:" + webServer.port() + path);
        return new Request.Builder().url(url);
    }

    static class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Interceptor.Chain chain) throws IOException {
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

    static OkHttpClient newOkHttpClient(boolean ssl) throws Exception {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        if (ssl) {
            SSLContext sslContext = setupSSLTrust();
            clientBuilder.sslSocketFactory(sslContext.getSocketFactory(), TRUST_MANAGER);
            clientBuilder.hostnameVerifier((host, session) -> host.equals("localhost"));
        }
        return clientBuilder.build();
    }
}
