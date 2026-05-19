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

package io.helidon.tests.integration.h2spec.client;

import java.net.URI;
import java.time.Duration;

import io.helidon.webclient.http2.Http2Client;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;

/**
 * Minimal Helidon HTTP/2 client launcher for upstream {@code h2specd}.
 * <p>
 * {@code h2specd} appends the target URL as the final command argument and expects the
 * launched client to perform one complete request/response exchange.
 */
public final class H2SpecClientMain {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    private H2SpecClientMain() {
    }

    /**
     * Run a single HTTP/2 request against the URL provided by {@code h2specd}.
     *
     * @param args command-line arguments, expecting the target URL as the final element
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: H2SpecClientMain <url>");
            System.exit(2);
        }

        URI uri = URI.create(args[args.length - 1]);
        boolean priorKnowledge = "http".equalsIgnoreCase(uri.getScheme());

        Http2ClientProtocolConfig protocolConfig = Http2ClientProtocolConfig.builder()
                .priorKnowledge(priorKnowledge)
                .build();

        Http2Client client = Http2Client.builder()
                .shareConnectionCache(false)
                .baseUri(uri.toString())
                .connectTimeout(REQUEST_TIMEOUT)
                .readTimeout(REQUEST_TIMEOUT)
                .protocolConfig(protocolConfig)
                .build();

        try (var response = client.get().request()) {
            // Consume the full body so the client observes the complete frame sequence under test.
            response.entity().as(byte[].class);
        } catch (Throwable t) {
            t.printStackTrace(System.err);
            System.exit(1);
        } finally {
            client.closeResource();
        }
    }
}
