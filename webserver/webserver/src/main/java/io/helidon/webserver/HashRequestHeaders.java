/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.ReadOnlyParameters;
import io.helidon.common.http.Utils;

/**
 * A {@link RequestHeaders} implementation on top of {@link ReadOnlyParameters}.
 */
class HashRequestHeaders extends ReadOnlyParameters implements RequestHeaders {

    private final Object internalLock = new Object();
    private volatile Parameters cookies;
    private List<MediaType> acceptedtypesCache;

    /**
     * Creates a new instance.
     */
    HashRequestHeaders() {
        this(null);
    }

    /**
     * Creates a new instance from provided data.
     * Initial data are copied.
     *
     * @param initialContent initial content.
     */
    HashRequestHeaders(Map<String, List<String>> initialContent) {
        super(initialContent);
    }

    @Override
    public Optional<MediaType> contentType() {
        return first(Http.Header.CONTENT_TYPE).map(MediaType::parse);
    }

    @Override
    public OptionalLong contentLength() {
        Optional<String> v = first(Http.Header.CONTENT_LENGTH);
        if (v.isPresent()) {
            return OptionalLong.of(Long.parseLong(v.get()));
        } else {
            return OptionalLong.empty();
        }
    }

    @Override
    public Parameters cookies() {
        if (cookies == null) {
            synchronized (internalLock) {
                if (cookies == null) {
                    List<Parameters> list = all(Http.Header.COOKIE).stream()
                                                    .map(CookieParser::parse)
                                                    .collect(Collectors.toList());
                    cookies = Parameters.toUnmodifiableParameters(HashParameters.concat(list));
                }
            }
        }
        return cookies;
    }

    @Override
    public List<MediaType> acceptedTypes() {
        List<MediaType> result = this.acceptedtypesCache;
        if (result == null) {
            result = all(Http.Header.ACCEPT).stream()
                            .flatMap(h -> Utils.tokenize(',', "\"", false, h).stream())
                            .map(String::trim)
                            .map(MediaType::parse)
                            .collect(Collectors.toList());
            result = Collections.unmodifiableList(result);
            this.acceptedtypesCache = result;
        }
        return result;
    }

    @Override
    public boolean isAccepted(MediaType mediaType) {
        Objects.requireNonNull(mediaType, "Parameter 'mediaType' is null!");
        List<MediaType> acceptedTypes = acceptedTypes();
        return acceptedTypes.isEmpty() || acceptedTypes.stream().anyMatch(mediaType);
    }

    @Override
    public Optional<MediaType> bestAccepted(MediaType... mediaTypes) {
        if (mediaTypes == null || mediaTypes.length == 0) {
            return Optional.empty();
        }
        List<MediaType> accepts = acceptedTypes();
        if (accepts == null || accepts.isEmpty()) {
            return Optional.ofNullable(mediaTypes[0]);
        }

        double best = 0;
        MediaType result = null;
        for (MediaType mt : mediaTypes) {
            if (mt != null) {
                for (MediaType acc : accepts) {
                    double q = acc.qualityFactor();
                    if (q > best && acc.test(mt)) {
                        if (q == 1) {
                            return Optional.of(mt);
                        } else {
                            best = q;
                            result = mt;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<ZonedDateTime> acceptDatetime() {
        return first(Http.Header.ACCEPT_DATETIME).map(Http.DateTime::parse);
    }

    @Override
    public Optional<ZonedDateTime> date() {
        return first(Http.Header.DATE).map(Http.DateTime::parse);
    }

    @Override
    public Optional<ZonedDateTime> ifModifiedSince() {
        return first(Http.Header.IF_MODIFIED_SINCE).map(Http.DateTime::parse);
    }

    @Override
    public Optional<ZonedDateTime> ifUnmodifiedSince() {
        return first(Http.Header.IF_UNMODIFIED_SINCE).map(Http.DateTime::parse);
    }

    @Override
    public Optional<URI> referer() {
        return first(Http.Header.REFERER).map(URI::create);
    }

    /**
     * Parse cookies based on RFC6265 but it can accepts also older formats including RFC2965 but skips parameters.
     */
    static class CookieParser {

        private CookieParser() {}

        private static final String RFC2965_VERSION = "$Version";
        private static final String RFC2965_PATH = "$Path";
        private static final String RFC2965_DOMAIN = "$Domain";
        private static final String RFC2965_PORT = "$Port";

        /**
         * Parse cookies based on RFC6265 but it can accepts also older formats including RFC2965 but skips parameters.
         *
         * <p>Multiple cookies can be returned in a single headers and a single cookie-name can have multiple values.
         * Note that base on RFC6265 an order of cookie values has no semantics.
         *
         * @param cookieHeaderValue a value of '{@code Cookie:}' header.
         * @return a cookie name and values parsed into a parameter format.
         */
        public static Parameters parse(String cookieHeaderValue) {
            if (cookieHeaderValue == null) {
                return empty();
            }
            cookieHeaderValue = cookieHeaderValue.trim();
            if (cookieHeaderValue.isEmpty()) {
                return empty();
            }

            // Beware RFC2965
            boolean isRfc2965 = false;
            if (cookieHeaderValue.regionMatches(true, 0, RFC2965_VERSION, 0, RFC2965_VERSION.length())) {
                isRfc2965 = true;
                int ind = cookieHeaderValue.indexOf(';');
                if (ind < 0) {
                    return empty();
                } else {
                    cookieHeaderValue = cookieHeaderValue.substring(ind + 1);
                }
            }

            Parameters result = new HashParameters();
            for (String baseToken : Utils.tokenize(',', "\"", false, cookieHeaderValue)) {
                for (String token : Utils.tokenize(';', "\"", false, baseToken)) {
                    int eqInd = token.indexOf('=');
                    if (eqInd > 0) {
                        String name = token.substring(0, eqInd).trim();
                        if (name.isEmpty()) {
                            continue; // Name MOST NOT be empty;
                        }
                        if (isRfc2965 && name.charAt(0) == '$'
                            && (RFC2965_PATH.equalsIgnoreCase(name) || RFC2965_DOMAIN.equalsIgnoreCase(name)
                                || RFC2965_PORT.equalsIgnoreCase(name) || RFC2965_VERSION.equalsIgnoreCase(name))) {
                            continue; // Skip RFC2965 attributes
                        }
                        String value = token.substring(eqInd + 1).trim();
                        result.add(name, unwrap(value));
                    }
                }
            }
            return result;
        }

        /**
         * Unwrap from double-quotes - if exists.
         *
         * @param str string to unwrap.
         * @return unwrapped string.
         */
        private static String unwrap(String str) {
            if (str == null) {
                return null;
            }
            if (str.length() >= 2 && '"' == str.charAt(0) && '"' == str.charAt(str.length() - 1)) {
                return str.substring(1, str.length() - 1);
            }
            return str;
        }
    }
}
