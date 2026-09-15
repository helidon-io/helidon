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

package io.helidon.http.http3.qpack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * QPACK static-table entries defined for HTTP/3 header compression.
 */
final class QpackStaticTable {
    private static final List<HeaderField> HTTP3_HEADER_FIELDS = List.of(
            new HeaderField(":authority"),
            new HeaderField(":path", "/"),
            new HeaderField("age", "0"),
            new HeaderField("content-disposition"),
            new HeaderField("content-length", "0"),
            new HeaderField("cookie"),
            new HeaderField("date"),
            new HeaderField("etag"),
            new HeaderField("if-modified-since"),
            new HeaderField("if-none-match"),
            new HeaderField("last-modified"),
            new HeaderField("link"),
            new HeaderField("location"),
            new HeaderField("referer"),
            new HeaderField("set-cookie"),
            new HeaderField(":method", "CONNECT"),
            new HeaderField(":method", "DELETE"),
            new HeaderField(":method", "GET"),
            new HeaderField(":method", "HEAD"),
            new HeaderField(":method", "OPTIONS"),
            new HeaderField(":method", "POST"),
            new HeaderField(":method", "PUT"),
            new HeaderField(":scheme", "http"),
            new HeaderField(":scheme", "https"),
            new HeaderField(":status", "103"),
            new HeaderField(":status", "200"),
            new HeaderField(":status", "304"),
            new HeaderField(":status", "404"),
            new HeaderField(":status", "503"),
            new HeaderField("accept", "*/*"),
            new HeaderField("accept", "application/dns-message"),
            new HeaderField("accept-encoding", "gzip, deflate, br"),
            new HeaderField("accept-ranges", "bytes"),
            new HeaderField("access-control-allow-headers", "cache-control"),
            new HeaderField("access-control-allow-headers", "content-type"),
            new HeaderField("access-control-allow-origin", "*"),
            new HeaderField("cache-control", "max-age=0"),
            new HeaderField("cache-control", "max-age=2592000"),
            new HeaderField("cache-control", "max-age=604800"),
            new HeaderField("cache-control", "no-cache"),
            new HeaderField("cache-control", "no-store"),
            new HeaderField("cache-control", "public, max-age=31536000"),
            new HeaderField("content-encoding", "br"),
            new HeaderField("content-encoding", "gzip"),
            new HeaderField("content-type", "application/dns-message"),
            new HeaderField("content-type", "application/javascript"),
            new HeaderField("content-type", "application/json"),
            new HeaderField("content-type", "application/x-www-form-urlencoded"),
            new HeaderField("content-type", "image/gif"),
            new HeaderField("content-type", "image/jpeg"),
            new HeaderField("content-type", "image/png"),
            new HeaderField("content-type", "text/css"),
            new HeaderField("content-type", "text/html; charset=utf-8"),
            new HeaderField("content-type", "text/plain"),
            new HeaderField("content-type", "text/plain;charset=utf-8"),
            new HeaderField("range", "bytes=0-"),
            new HeaderField("strict-transport-security", "max-age=31536000"),
            new HeaderField("strict-transport-security", "max-age=31536000; includesubdomains"),
            new HeaderField("strict-transport-security", "max-age=31536000; includesubdomains; preload"),
            new HeaderField("vary", "accept-encoding"),
            new HeaderField("vary", "origin"),
            new HeaderField("x-content-type-options", "nosniff"),
            new HeaderField("x-xss-protection", "1; mode=block"),
            new HeaderField(":status", "100"),
            new HeaderField(":status", "204"),
            new HeaderField(":status", "206"),
            new HeaderField(":status", "302"),
            new HeaderField(":status", "400"),
            new HeaderField(":status", "403"),
            new HeaderField(":status", "421"),
            new HeaderField(":status", "425"),
            new HeaderField(":status", "500"),
            new HeaderField("accept-language"),
            new HeaderField("access-control-allow-credentials", "FALSE"),
            new HeaderField("access-control-allow-credentials", "TRUE"),
            new HeaderField("access-control-allow-headers", "*"),
            new HeaderField("access-control-allow-methods", "get"),
            new HeaderField("access-control-allow-methods", "get, post, options"),
            new HeaderField("access-control-allow-methods", "options"),
            new HeaderField("access-control-expose-headers", "content-length"),
            new HeaderField("access-control-request-headers", "content-type"),
            new HeaderField("access-control-request-method", "get"),
            new HeaderField("access-control-request-method", "post"),
            new HeaderField("alt-svc", "clear"),
            new HeaderField("authorization"),
            new HeaderField("content-security-policy", "script-src 'none'; object-src 'none'; base-uri 'none'"),
            new HeaderField("early-data", "1"),
            new HeaderField("expect-ct"),
            new HeaderField("forwarded"),
            new HeaderField("if-range"),
            new HeaderField("origin"),
            new HeaderField("purpose", "prefetch"),
            new HeaderField("server"),
            new HeaderField("timing-allow-origin", "*"),
            new HeaderField("upgrade-insecure-requests", "1"),
            new HeaderField("user-agent"),
            new HeaderField("x-forwarded-for"),
            new HeaderField("x-frame-options", "deny"),
            new HeaderField("x-frame-options", "sameorigin")
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
    public static HeaderField get(long index) {
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
    public static long indexOf(String name, String value) {
        HeaderIndices indices = indices(name);
        return indices == null ? -1 : indices.exactIndex(value);
    }

    /**
     * Return the first static-table index for the provided header name.
     *
     * @param name header name
     * @return name index, or {@code -1} when absent
     */
    public static long nameIndex(String name) {
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
            HeaderField field = HTTP3_HEADER_FIELDS.get(i);
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
