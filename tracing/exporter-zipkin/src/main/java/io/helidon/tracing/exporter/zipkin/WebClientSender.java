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

package io.helidon.tracing.exporter.zipkin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.http1.Http1Client;

import zipkin2.reporter.BytesMessageSender;

@SuppressWarnings("removal")
final class WebClientSender extends BytesMessageSender.Base {
    private static final int MAX_REDIRECTS = 20;

    private final WebClient client;
    private final URI endpoint;
    private final boolean gzip;

    WebClientSender(ZipkinSpanExporterConfig config) {
        super(config.encoder().orElse(config.encoding()).encoding());
        endpoint = URI.create(config.endpoint());
        gzip = config.compression().equals("gzip");
        client = WebClient.builder()
                .protocolPreference(List.of(Http1Client.PROTOCOL_ID))
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(ZipkinExporterSupport.readTimeout(config.timeout()))
                .shareConnectionCache(false)
                .followRedirects(false)
                .build();
    }

    @Override
    public int messageMaxBytes() {
        return 500_000;
    }

    @Override
    public void send(List<byte[]> encodedSpans) throws IOException {
        byte[] body = encoding.encode(encodedSpans);
        if (gzip) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream output = new GZIPOutputStream(bytes)) {
                output.write(body);
            }
            body = bytes.toByteArray();
        }

        URI uri = endpoint;
        Method method = Method.POST;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpClientRequest request = client.method(method)
                    .uri(uri)
                    .header(HeaderNames.create("b3"), "0");
            if (method == Method.POST) {
                request.header(HeaderNames.CONTENT_TYPE, encoding.mediaType());
                if (gzip) {
                    request.header(HeaderNames.CONTENT_ENCODING, "gzip");
                }
            }
            try (HttpClientResponse response = method == Method.POST ? request.submit(body) : request.request()) {
                int status = response.status().code();
                // Drain without retaining or disclosing the collector's response body.
                response.entity().inputStream().transferTo(OutputStream.nullOutputStream());
                if (status >= 200 && status < 300) {
                    return;
                }
                // OkHttp changes POST to GET for 300/301/302/303, but does not replay POST for 307/308.
                boolean redirect = status == 300 || status == 301 || status == 302 || status == 303
                        || (method == Method.GET && (status == 307 || status == 308));
                var location = response.headers().first(HeaderNames.LOCATION);
                if (!redirect || location.isEmpty()) {
                    throw new IOException("Zipkin collector returned HTTP status " + status);
                }
                URI target;
                try {
                    target = uri.resolve(location.get());
                } catch (IllegalArgumentException e) {
                    throw new IOException("Zipkin collector returned an invalid redirect URI");
                }
                if ((!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme()))
                        || !ZipkinExporterSupport.hasHost(target)) {
                    throw new IOException("Zipkin collector redirect must have an HTTP or HTTPS endpoint with a host");
                }
                uri = target;
                method = Method.GET;
            }
        }
        throw new IOException("Zipkin collector exceeded the maximum of 20 redirects");
    }

    @Override
    public void close() {
        // The exporter has stopped admission and drained all requests before releasing the cache.
        client.closeResource();
    }
}
