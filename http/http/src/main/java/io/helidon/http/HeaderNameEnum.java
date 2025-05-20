/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.HashMap;
import java.util.Map;

import io.helidon.common.buffers.Ascii;

/*
 * Do not add random headers here. These headers are optimized for performance, and each header added to this enum
 * will slightly increase the memory used by each HTTP request.
 */
enum HeaderNameEnum implements HeaderName {
    ACCEPT("Accept"),
    ACCEPT_CHARSET("Accept-Charset"),
    ACCEPT_ENCODING("Accept-Encoding"),
    ACCEPT_LANGUAGE("Accept-Language"),
    ACCEPT_DATETIME("Accept-Datetime"),
    ACCESS_CONTROL_ALLOW_CREDENTIALS("Access-Control-Allow-Credentials"),
    ACCESS_CONTROL_ALLOW_HEADERS("Access-Control-Allow-Headers"),
    ACCESS_CONTROL_ALLOW_METHODS("Access-Control-Allow-Methods"),
    ACCESS_CONTROL_ALLOW_ORIGIN("Access-Control-Allow-Origin"),
    ACCESS_CONTROL_EXPOSE_HEADERS("Access-Control-Expose-Headers"),
    ACCESS_CONTROL_MAX_AGE("Access-Control-Max-Age"),
    ACCESS_CONTROL_REQUEST_HEADERS("Access-Control-Request-Headers"),
    ACCESS_CONTROL_REQUEST_METHOD("Access-Control-Request-Method"),
    AUTHORIZATION("Authorization"),
    COOKIE("Cookie"),
    EXPECT("Expect"),
    FORWARDED("Forwarded"),
    FROM("From"),
    HOST(HeaderNames.HOST_STRING),
    IF_MATCH("If-Match"),
    IF_MODIFIED_SINCE("If-Modified-Since"),
    IF_NONE_MATCH("If-None-Match"),
    IF_RANGE("If-Range"),
    IF_UNMODIFIED_SINCE("If-Unmodified-Since"),
    MAX_FORWARDS("Max-Forwards"),
    ORIGIN("Origin"),
    PROXY_AUTHENTICATE("Proxy-Authenticate"),
    PROXY_AUTHORIZATION("Proxy-Authorization"),
    RANGE("Range"),
    REFERER("Referer"),
    REFRESH("Refresh"),
    TE("TE"),
    USER_AGENT("User-Agent"),
    VIA("Via"),
    ACCEPT_PATCH("Accept-Patch"),
    ACCEPT_RANGES("Accept-Ranges"),
    AGE("Age"),
    ALLOW("Allow"),
    ALT_SVC("Alt-Svc"),
    CACHE_CONTROL("Cache-Control"),
    CONNECTION("Connection"),
    CONTENT_DISPOSITION("Content-Disposition"),
    CONTENT_ENCODING("Content-Encoding"),
    CONTENT_LANGUAGE("Content-Language"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_LOCATION("aa"),
    CONTENT_RANGE("Content-Range"),
    CONTENT_TYPE("Content-Type"),
    DATE("Date"),
    ETAG("ETag"),
    EXPIRES("Expires"),
    LAST_MODIFIED("Last-Modified"),
    LINK("Link"),
    LOCATION("Location"),
    PRAGMA("Pragma"),
    PUBLIC_KEY_PINS("Public-Key-Pins"),
    RETRY_AFTER("Retry-After"),
    SERVER("Server"),
    SET_COOKIE("Set-Cookie"),
    SET_COOKIE2("Set-Cookie2"),
    STRICT_TRANSPORT_SECURITY("Strict-Transport-Security"),
    TRAILER("Trailer"),
    TRANSFER_ENCODING("Transfer-Encoding"),
    TSV("TSV"),
    UPGRADE("Upgrade"),
    VARY("Vary"),
    WARNING("Warning"),
    WWW_AUTHENTICATE("WWW-Authenticate"),
    X_CONTENT_TYPE_OPTIONS("X-Content-Type-Options"),
    X_FORWARDED_FOR("X-Forwarded-For"),
    X_FORWARDED_HOST("X-Forwarded-Host"),
    X_FORWARDED_PORT("X-Forwarded-Port"),
    X_FORWARDED_PREFIX("X-Forwarded-Prefix"),
    X_FORWARDED_PROTO("X-Forwarded-Proto"),
    X_HELIDON_CN("X-HELIDON-CN");

    private static final Map<String, HeaderName> BY_NAME;
    private static final Map<String, HeaderName> BY_CAP_NAME;

    static {
        Map<String, HeaderName> byName = new HashMap<>();
        Map<String, HeaderName> byCapName = new HashMap<>();
        for (HeaderNameEnum value : HeaderNameEnum.values()) {
            byName.put(value.lowerCase(), value);
            byCapName.put(value.defaultCase(), value);
        }
        BY_NAME = byName;
        BY_CAP_NAME = byCapName;
    }

    private final String lowerCase;
    private final String http1Case;
    private final int index;

    HeaderNameEnum(String http1Case) {
        this.http1Case = http1Case;
        this.lowerCase = this.http1Case.toLowerCase();
        this.index = this.ordinal();
    }

    static HeaderName byCapitalizedName(String name) {
        HeaderName found = BY_CAP_NAME.get(name);
        if (found == null) {
            return byName(Ascii.toLowerCase(name));
        }
        return found;
    }

    static HeaderName byName(String lowerCase) {
        return BY_NAME.get(lowerCase);
    }

    @Override
    public String lowerCase() {
        return lowerCase;
    }

    @Override
    public String defaultCase() {
        return http1Case;
    }

    @Override
    public int index() {
        return index;
    }
}
