/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
package io.helidon.grpc.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.helidon.http.HeaderNames;
import io.helidon.http.WritableHeaders;
import io.helidon.http.http2.Http2Headers;

import io.grpc.Metadata;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcHeadersUtilTest {

    @Test
    void testUpdateHeaders() {
        Metadata metadata = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "sugar");
        metadata.put(key, "almond");
        WritableHeaders<?> headers = WritableHeaders.create();
        GrpcHeadersUtil.updateHeaders(headers, metadata);
        // there is exactly one header name: `Cookie`
        assertThat(headers.size(), is(1));
        List<String> values = headers.get(HeaderNames.COOKIE).allValues();
        assertThat(values, hasItem("sugar"));
        assertThat(values, hasItem("almond"));
    }

    @Test
    void testUpdateBinaryHeaders() {
        Metadata metadata = new Metadata();
        Metadata.Key<byte[]> key = Metadata.Key.of("secret-bin", Metadata.BINARY_BYTE_MARSHALLER);
        byte[] mySecret = "my-secret".getBytes(StandardCharsets.UTF_8);
        byte[] myOtherSecret = "my-other-secret".getBytes(StandardCharsets.UTF_8);
        metadata.put(key, mySecret);
        metadata.put(key, myOtherSecret);
        WritableHeaders<?> headers = WritableHeaders.create();
        GrpcHeadersUtil.updateHeaders(headers, metadata);
        assertThat(headers.size(), is(1));
        List<String> values = headers.get(HeaderNames.create("secret-bin")).allValues();
        Set<String> decoded = new HashSet<>();
        values.stream()
                .map(Base64.getDecoder()::decode)
                .map(value -> new String(value, StandardCharsets.UTF_8))
                .forEach(decoded::add);
        assertThat(decoded, hasItem("my-secret"));
        assertThat(decoded, hasItem("my-other-secret"));
    }

    @Test
    void testToMetadata() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.COOKIE, "sugar", "almond");
        Http2Headers http2Headers = mock(Http2Headers.class);
        when(http2Headers.httpHeaders()).thenReturn(headers);
        Metadata metadata = GrpcHeadersUtil.toMetadata(http2Headers);
        Metadata.Key<String> key = Metadata.Key.of("cookie", Metadata.ASCII_STRING_MARSHALLER);
        assertThat(metadata.containsKey(key), is(true));
        Set<String> values = new HashSet<>();
        metadata.getAll(key).forEach(values::add);
        assertThat(values, hasItem("sugar"));
        assertThat(values, hasItem("almond"));
    }

    @Test
    void testToBinaryMetadata() {
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.create("secret-bin"),
                    Base64.getEncoder().encodeToString("sugar".getBytes(StandardCharsets.UTF_8)),
                    Base64.getEncoder().encodeToString("almond".getBytes(StandardCharsets.UTF_8)));

        Metadata metadata = GrpcHeadersUtil.toMetadata(headers);
        Metadata.Key<byte[]> key = Metadata.Key.of("secret-bin", Metadata.BINARY_BYTE_MARSHALLER);
        Set<String> values = new HashSet<>();
        metadata.getAll(key).forEach(value -> values.add(new String(value, StandardCharsets.UTF_8)));

        assertThat(values, hasItem("sugar"));
        assertThat(values, hasItem("almond"));
    }

    @Test
    void testToCommaSeparatedBinaryMetadata() {
        String sugar = Base64.getEncoder().withoutPadding().encodeToString("sugar".getBytes(StandardCharsets.UTF_8));
        String almond = Base64.getEncoder().encodeToString("almond".getBytes(StandardCharsets.UTF_8));
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.create("secret-bin"), sugar + ", " + almond);

        Metadata metadata = GrpcHeadersUtil.toMetadata(headers);
        Metadata.Key<byte[]> key = Metadata.Key.of("secret-bin", Metadata.BINARY_BYTE_MARSHALLER);
        Set<String> values = new HashSet<>();
        metadata.getAll(key).forEach(value -> values.add(new String(value, StandardCharsets.UTF_8)));

        assertThat(values, hasItem("sugar"));
        assertThat(values, hasItem("almond"));
    }

    @Test
    void testToCommaSeparatedBinaryMetadataPreservesTrailingEmptyValue() {
        String sugar = Base64.getEncoder().encodeToString("sugar".getBytes(StandardCharsets.UTF_8));
        WritableHeaders<?> headers = WritableHeaders.create();
        headers.add(HeaderNames.create("secret-bin"), sugar + ",");

        Metadata metadata = GrpcHeadersUtil.toMetadata(headers);
        Metadata.Key<byte[]> key = Metadata.Key.of("secret-bin", Metadata.BINARY_BYTE_MARSHALLER);
        Set<String> values = new HashSet<>();
        metadata.getAll(key).forEach(value -> values.add(new String(value, StandardCharsets.UTF_8)));

        assertThat(values, hasItems("sugar", ""));
        assertThat(values.size(), is(2));
    }
}
