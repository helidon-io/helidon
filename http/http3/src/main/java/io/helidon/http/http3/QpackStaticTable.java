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

package io.helidon.http.http3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * QPACK static-table entries defined for HTTP/3 header compression.
 */
final class QpackStaticTable {
    private static final List<QpackCodec.HeaderField> HTTP3_HEADER_FIELDS = List.of(
            new QpackCodec.HeaderField(":authority"),
            new QpackCodec.HeaderField(":path", "/"),
            new QpackCodec.HeaderField("age", "0"),
            new QpackCodec.HeaderField("content-disposition"),
            new QpackCodec.HeaderField("content-length", "0"),
            new QpackCodec.HeaderField("cookie"),
            new QpackCodec.HeaderField("date"),
            new QpackCodec.HeaderField("etag"),
            new QpackCodec.HeaderField("if-modified-since"),
            new QpackCodec.HeaderField("if-none-match"),
            new QpackCodec.HeaderField("last-modified"),
            new QpackCodec.HeaderField("link"),
            new QpackCodec.HeaderField("location"),
            new QpackCodec.HeaderField("referer"),
            new QpackCodec.HeaderField("set-cookie"),
            new QpackCodec.HeaderField(":method", "CONNECT"),
            new QpackCodec.HeaderField(":method", "DELETE"),
            new QpackCodec.HeaderField(":method", "GET"),
            new QpackCodec.HeaderField(":method", "HEAD"),
            new QpackCodec.HeaderField(":method", "OPTIONS"),
            new QpackCodec.HeaderField(":method", "POST"),
            new QpackCodec.HeaderField(":method", "PUT"),
            new QpackCodec.HeaderField(":scheme", "http"),
            new QpackCodec.HeaderField(":scheme", "https"),
            new QpackCodec.HeaderField(":status", "103"),
            new QpackCodec.HeaderField(":status", "200"),
            new QpackCodec.HeaderField(":status", "304"),
            new QpackCodec.HeaderField(":status", "404"),
            new QpackCodec.HeaderField(":status", "503"),
            new QpackCodec.HeaderField("accept", "*/*"),
            new QpackCodec.HeaderField("accept", "application/dns-message"),
            new QpackCodec.HeaderField("accept-encoding", "gzip, deflate, br"),
            new QpackCodec.HeaderField("accept-ranges", "bytes"),
            new QpackCodec.HeaderField("access-control-allow-headers", "cache-control"),
            new QpackCodec.HeaderField("access-control-allow-headers", "content-type"),
            new QpackCodec.HeaderField("access-control-allow-origin", "*"),
            new QpackCodec.HeaderField("cache-control", "max-age=0"),
            new QpackCodec.HeaderField("cache-control", "max-age=2592000"),
            new QpackCodec.HeaderField("cache-control", "max-age=604800"),
            new QpackCodec.HeaderField("cache-control", "no-cache"),
            new QpackCodec.HeaderField("cache-control", "no-store"),
            new QpackCodec.HeaderField("cache-control", "public, max-age=31536000"),
            new QpackCodec.HeaderField("content-encoding", "br"),
            new QpackCodec.HeaderField("content-encoding", "gzip"),
            new QpackCodec.HeaderField("content-type", "application/dns-message"),
            new QpackCodec.HeaderField("content-type", "application/javascript"),
            new QpackCodec.HeaderField("content-type", "application/json"),
            new QpackCodec.HeaderField("content-type", "application/x-www-form-urlencoded"),
            new QpackCodec.HeaderField("content-type", "image/gif"),
            new QpackCodec.HeaderField("content-type", "image/jpeg"),
            new QpackCodec.HeaderField("content-type", "image/png"),
            new QpackCodec.HeaderField("content-type", "text/css"),
            new QpackCodec.HeaderField("content-type", "text/html; charset=utf-8"),
            new QpackCodec.HeaderField("content-type", "text/plain"),
            new QpackCodec.HeaderField("content-type", "text/plain;charset=utf-8"),
            new QpackCodec.HeaderField("range", "bytes=0-"),
            new QpackCodec.HeaderField("strict-transport-security", "max-age=31536000"),
            new QpackCodec.HeaderField("strict-transport-security", "max-age=31536000; includesubdomains"),
            new QpackCodec.HeaderField("strict-transport-security", "max-age=31536000; includesubdomains; preload"),
            new QpackCodec.HeaderField("vary", "accept-encoding"),
            new QpackCodec.HeaderField("vary", "origin"),
            new QpackCodec.HeaderField("x-content-type-options", "nosniff"),
            new QpackCodec.HeaderField("x-xss-protection", "1; mode=block"),
            new QpackCodec.HeaderField(":status", "100"),
            new QpackCodec.HeaderField(":status", "204"),
            new QpackCodec.HeaderField(":status", "206"),
            new QpackCodec.HeaderField(":status", "302"),
            new QpackCodec.HeaderField(":status", "400"),
            new QpackCodec.HeaderField(":status", "403"),
            new QpackCodec.HeaderField(":status", "421"),
            new QpackCodec.HeaderField(":status", "425"),
            new QpackCodec.HeaderField(":status", "500"),
            new QpackCodec.HeaderField("accept-language"),
            new QpackCodec.HeaderField("access-control-allow-credentials", "FALSE"),
            new QpackCodec.HeaderField("access-control-allow-credentials", "TRUE"),
            new QpackCodec.HeaderField("access-control-allow-headers", "*"),
            new QpackCodec.HeaderField("access-control-allow-methods", "get"),
            new QpackCodec.HeaderField("access-control-allow-methods", "get, post, options"),
            new QpackCodec.HeaderField("access-control-allow-methods", "options"),
            new QpackCodec.HeaderField("access-control-expose-headers", "content-length"),
            new QpackCodec.HeaderField("access-control-request-headers", "content-type"),
            new QpackCodec.HeaderField("access-control-request-method", "get"),
            new QpackCodec.HeaderField("access-control-request-method", "post"),
            new QpackCodec.HeaderField("alt-svc", "clear"),
            new QpackCodec.HeaderField("authorization"),
            new QpackCodec.HeaderField("content-security-policy", "script-src 'none'; object-src 'none'; base-uri 'none'"),
            new QpackCodec.HeaderField("early-data", "1"),
            new QpackCodec.HeaderField("expect-ct"),
            new QpackCodec.HeaderField("forwarded"),
            new QpackCodec.HeaderField("if-range"),
            new QpackCodec.HeaderField("origin"),
            new QpackCodec.HeaderField("purpose", "prefetch"),
            new QpackCodec.HeaderField("server"),
            new QpackCodec.HeaderField("timing-allow-origin", "*"),
            new QpackCodec.HeaderField("upgrade-insecure-requests", "1"),
            new QpackCodec.HeaderField("user-agent"),
            new QpackCodec.HeaderField("x-forwarded-for"),
            new QpackCodec.HeaderField("x-frame-options", "deny"),
            new QpackCodec.HeaderField("x-frame-options", "sameorigin")
    );
    private static final Map<String, HeaderIndices> HTTP3_HEADER_INDICES = createHeaderIndices();

    private QpackStaticTable() {
    }

    /**
     * Return the static-table entry at the provided index.
     *
     * @param index static-table index
     * @return static-table entry
     */
    static QpackCodec.HeaderField get(long index) {
        if (index < 0 || index >= HTTP3_HEADER_FIELDS.size()) {
            throw new IllegalArgumentException("Invalid QPACK static table index: " + index);
        }
        return HTTP3_HEADER_FIELDS.get((int) index);
    }

    /**
     * Return the static-table index of an exact name/value match.
     *
     * @param name header name
     * @param value header value
     * @return exact-match index, or {@code -1} when absent
     */
    static long indexOf(String name, String value) {
        HeaderIndices indices = indices(name);
        return indices == null ? -1 : indices.exactIndex(value);
    }

    /**
     * Return the first static-table index for the provided header name.
     *
     * @param name header name
     * @return name index, or {@code -1} when absent
     */
    static long nameIndex(String name) {
        HeaderIndices indices = indices(name);
        return indices == null ? -1 : indices.nameIndex();
    }

    static HeaderIndices indices(String name) {
        return HTTP3_HEADER_INDICES.get(Objects.requireNonNull(name));
    }

    static int size() {
        return HTTP3_HEADER_FIELDS.size();
    }

    private static Map<String, HeaderIndices> createHeaderIndices() {
        Map<String, MutableHeaderIndices> mutableIndices = new HashMap<>();
        for (int i = 0; i < HTTP3_HEADER_FIELDS.size(); i++) {
            QpackCodec.HeaderField field = HTTP3_HEADER_FIELDS.get(i);
            MutableHeaderIndices indices = mutableIndices.get(field.name());
            if (indices == null) {
                indices = new MutableHeaderIndices(i, new HashMap<>());
                mutableIndices.put(field.name(), indices);
            }
            indices.exactIndices().putIfAbsent(field.value(), i);
        }

        Map<String, HeaderIndices> indices = new HashMap<>();
        mutableIndices.forEach((name, mutable) -> indices.put(
                name,
                new HeaderIndices(mutable.nameIndex(), mutable.exactIndices())));
        return indices;
    }

    static final class HeaderIndices {
        private final int nameIndex;
        private final Map<String, Integer> exactIndices;

        private HeaderIndices(int nameIndex, Map<String, Integer> exactIndices) {
            this.nameIndex = nameIndex;
            this.exactIndices = exactIndices;
        }

        int nameIndex() {
            return nameIndex;
        }

        int exactIndex(String value) {
            Integer index = exactIndices.get(Objects.requireNonNull(value));
            return index == null ? -1 : index;
        }
    }

    private record MutableHeaderIndices(int nameIndex, Map<String, Integer> exactIndices) {
    }
}
