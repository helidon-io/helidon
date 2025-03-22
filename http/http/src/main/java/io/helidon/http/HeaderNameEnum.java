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
    ACCEPT(Strings.ACCEPT_NAME),
    ACCEPT_CHARSET(Strings.ACCEPT_CHARSET_NAME),
    ACCEPT_ENCODING(Strings.ACCEPT_ENCODING_NAME),
    ACCEPT_LANGUAGE(Strings.ACCEPT_LANGUAGE_NAME),
    ACCEPT_DATETIME(Strings.ACCEPT_DATETIME_NAME),
    ACCESS_CONTROL_ALLOW_CREDENTIALS(Strings.ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME),
    ACCESS_CONTROL_ALLOW_HEADERS(Strings.ACCESS_CONTROL_ALLOW_HEADERS_NAME),
    ACCESS_CONTROL_ALLOW_METHODS(Strings.ACCESS_CONTROL_ALLOW_METHODS_NAME),
    ACCESS_CONTROL_ALLOW_ORIGIN(Strings.ACCESS_CONTROL_ALLOW_ORIGIN_NAME),
    ACCESS_CONTROL_EXPOSE_HEADERS(Strings.ACCESS_CONTROL_EXPOSE_HEADERS_NAME),
    ACCESS_CONTROL_MAX_AGE(Strings.ACCESS_CONTROL_MAX_AGE_NAME),
    ACCESS_CONTROL_REQUEST_HEADERS(Strings.ACCESS_CONTROL_REQUEST_HEADERS_NAME),
    ACCESS_CONTROL_REQUEST_METHOD(Strings.ACCESS_CONTROL_REQUEST_METHOD_NAME),
    AUTHORIZATION(Strings.AUTHORIZATION_NAME),
    COOKIE(Strings.COOKIE_NAME),
    EXPECT(Strings.EXPECT_NAME),
    FORWARDED(Strings.FORWARDED_NAME),
    FROM(Strings.FROM_NAME),
    HOST(Strings.HOST_NAME),
    IF_MATCH(Strings.IF_MATCH_NAME),
    IF_MODIFIED_SINCE(Strings.IF_MODIFIED_SINCE_NAME),
    IF_NONE_MATCH(Strings.IF_NONE_MATCH_NAME),
    IF_RANGE(Strings.IF_RANGE_NAME),
    IF_UNMODIFIED_SINCE(Strings.IF_UNMODIFIED_SINCE_NAME),
    MAX_FORWARDS(Strings.MAX_FORWARDS_NAME),
    ORIGIN(Strings.ORIGIN_NAME),
    PROXY_AUTHENTICATE(Strings.PROXY_AUTHENTICATE_NAME),
    PROXY_AUTHORIZATION(Strings.PROXY_AUTHORIZATION_NAME),
    RANGE(Strings.RANGE_NAME),
    REFERER(Strings.REFERER_NAME),
    REFRESH(Strings.REFRESH_NAME),
    TE(Strings.TE_NAME),
    USER_AGENT(Strings.USER_AGENT_NAME),
    VIA(Strings.VIA_NAME),
    ACCEPT_PATCH(Strings.ACCEPT_PATCH_NAME),
    ACCEPT_RANGES(Strings.ACCEPT_RANGES_NAME),
    AGE(Strings.AGE_NAME),
    ALLOW(Strings.ALLOW_NAME),
    ALT_SVC(Strings.ALT_SVC_NAME),
    CACHE_CONTROL(Strings.CACHE_CONTROL_NAME),
    CONNECTION(Strings.CONNECTION_NAME),
    CONTENT_DISPOSITION(Strings.CONTENT_DISPOSITION_NAME),
    CONTENT_ENCODING(Strings.CONTENT_ENCODING_NAME),
    CONTENT_LANGUAGE(Strings.CONTENT_LANGUAGE_NAME),
    CONTENT_LENGTH(Strings.CONTENT_LENGTH_NAME),
    CONTENT_LOCATION(Strings.CONTENT_LOCATION_NAME),
    CONTENT_RANGE(Strings.CONTENT_RANGE_NAME),
    CONTENT_TYPE(Strings.CONTENT_TYPE_NAME),
    DATE(Strings.DATE_NAME),
    ETAG(Strings.ETAG_NAME),
    EXPIRES(Strings.EXPIRES_NAME),
    LAST_MODIFIED(Strings.LAST_MODIFIED_NAME),
    LINK(Strings.LINK_NAME),
    LOCATION(Strings.LOCATION_NAME),
    PRAGMA(Strings.PRAGMA_NAME),
    PUBLIC_KEY_PINS(Strings.PUBLIC_KEY_PINS_NAME),
    RETRY_AFTER(Strings.RETRY_AFTER_NAME),
    SERVER(Strings.SERVER_NAME),
    SET_COOKIE(Strings.SET_COOKIE_NAME),
    SET_COOKIE2(Strings.SET_COOKIE2_NAME),
    STRICT_TRANSPORT_SECURITY(Strings.STRICT_TRANSPORT_SECURITY_NAME),
    TRAILER(Strings.TRAILER_NAME),
    TRANSFER_ENCODING(Strings.TRANSFER_ENCODING_NAME),
    TSV(Strings.TSV_NAME),
    UPGRADE(Strings.UPGRADE_NAME),
    VARY(Strings.VARY_NAME),
    WARNING(Strings.WARNING_NAME),
    WWW_AUTHENTICATE(Strings.WWW_AUTHENTICATE_NAME),
    X_FORWARDED_FOR(Strings.X_FORWARDED_FOR_NAME),
    X_FORWARDED_HOST(Strings.X_FORWARDED_HOST_NAME),
    X_FORWARDED_PORT(Strings.X_FORWARDED_PORT_NAME),
    X_FORWARDED_PREFIX(Strings.X_FORWARDED_PREFIX_NAME),
    X_FORWARDED_PROTO(Strings.X_FORWARDED_PROTO_NAME),
    X_HELIDON_CN(Strings.X_HELIDON_CN_NAME);

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

    static class Strings {
        static final String ACCEPT_NAME = "Accept";
        static final String ACCEPT_CHARSET_NAME = "Accept-Charset";
        static final String ACCEPT_ENCODING_NAME = "Accept-Encoding";
        static final String ACCEPT_LANGUAGE_NAME = "Accept-Language";
        static final String ACCEPT_DATETIME_NAME = "Accept-Datetime";
        static final String ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME = "Access-Control-Allow-Credentials";
        static final String ACCESS_CONTROL_ALLOW_HEADERS_NAME = "Access-Control-Allow-Headers";
        static final String ACCESS_CONTROL_ALLOW_METHODS_NAME = "Access-Control-Allow-Methods";
        static final String ACCESS_CONTROL_ALLOW_ORIGIN_NAME = "Access-Control-Allow-Origin";
        static final String ACCESS_CONTROL_EXPOSE_HEADERS_NAME = "Access-Control-Expose-Headers";
        static final String ACCESS_CONTROL_MAX_AGE_NAME = "Access-Control-Max-Age";
        static final String ACCESS_CONTROL_REQUEST_HEADERS_NAME = "Access-Control-Request-Headers";
        static final String ACCESS_CONTROL_REQUEST_METHOD_NAME = "Access-Control-Request-Method";
        static final String AUTHORIZATION_NAME = "Authorization";
        static final String COOKIE_NAME = "Cookie";
        static final String EXPECT_NAME = "Expect";
        static final String FORWARDED_NAME = "Forwarded";
        static final String FROM_NAME = "From";
        static final String HOST_NAME = "Host";
        static final String IF_MATCH_NAME = "If-Match";
        static final String IF_MODIFIED_SINCE_NAME = "If-Modified-Since";
        static final String IF_NONE_MATCH_NAME = "If-None-Match";
        static final String IF_RANGE_NAME = "If-Range";
        static final String IF_UNMODIFIED_SINCE_NAME = "If-Unmodified-Since";
        static final String MAX_FORWARDS_NAME = "Max-Forwards";
        static final String ORIGIN_NAME = "Origin";
        static final String PROXY_AUTHENTICATE_NAME = "Proxy-Authenticate";
        static final String PROXY_AUTHORIZATION_NAME = "Proxy-Authorization";
        static final String RANGE_NAME = "Range";
        static final String REFERER_NAME = "Referer";
        static final String REFRESH_NAME = "Refresh";
        static final String TE_NAME = "TE";
        static final String USER_AGENT_NAME = "User-Agent";
        static final String VIA_NAME = "Via";
        static final String ACCEPT_PATCH_NAME = "Accept-Patch";
        static final String ACCEPT_RANGES_NAME = "Accept-Ranges";
        static final String AGE_NAME = "Age";
        static final String ALLOW_NAME = "Allow";
        static final String ALT_SVC_NAME = "Alt-Svc";
        static final String CACHE_CONTROL_NAME = "Cache-Control";
        static final String CONNECTION_NAME = "Connection";
        static final String CONTENT_DISPOSITION_NAME = "Content-Disposition";
        static final String CONTENT_ENCODING_NAME = "Content-Encoding";
        static final String CONTENT_LANGUAGE_NAME = "Content-Language";
        static final String CONTENT_LENGTH_NAME = "Content-Length";
        static final String CONTENT_LOCATION_NAME = "aa";
        static final String CONTENT_RANGE_NAME = "Content-Range";
        static final String CONTENT_TYPE_NAME = "Content-Type";
        static final String DATE_NAME = "Date";
        static final String ETAG_NAME = "ETag";
        static final String EXPIRES_NAME = "Expires";
        static final String LAST_MODIFIED_NAME = "Last-Modified";
        static final String LINK_NAME = "Link";
        static final String LOCATION_NAME = "Location";
        static final String PRAGMA_NAME = "Pragma";
        static final String PUBLIC_KEY_PINS_NAME = "Public-Key-Pins";
        static final String RETRY_AFTER_NAME = "Retry-After";
        static final String SERVER_NAME = "Server";
        static final String SET_COOKIE_NAME = "Set-Cookie";
        static final String SET_COOKIE2_NAME = "Set-Cookie2";
        static final String STRICT_TRANSPORT_SECURITY_NAME = "Strict-Transport-Security";
        static final String TRAILER_NAME = "Trailer";
        static final String TRANSFER_ENCODING_NAME = "Transfer-Encoding";
        static final String TSV_NAME = "TSV";
        static final String UPGRADE_NAME = "Upgrade";
        static final String VARY_NAME = "Vary";
        static final String WARNING_NAME = "Warning";
        static final String WWW_AUTHENTICATE_NAME = "WWW-Authenticate";
        static final String X_FORWARDED_FOR_NAME = "X-Forwarded-For";
        static final String X_FORWARDED_HOST_NAME = "X-Forwarded-Host";
        static final String X_FORWARDED_PORT_NAME = "X-Forwarded-Port";
        static final String X_FORWARDED_PREFIX_NAME = "X-Forwarded-Prefix";
        static final String X_FORWARDED_PROTO_NAME = "X-Forwarded-Proto";
        static final String X_HELIDON_CN_NAME = "X-HELIDON-CN";

        private Strings() {
        }
    }
}
