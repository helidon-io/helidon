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

package io.helidon.http;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import io.helidon.common.Api;

/**
 * Header formatter for protocol logs.
 */
@Api.Internal
public final class LogFormatter {
    private static final Set<HeaderName> DEFAULT_SAFE_HEADER_NAMES = Set.of(HeaderNames.ACCEPT,
                                                                            HeaderNames.ACCEPT_ENCODING,
                                                                            HeaderNames.ACCEPT_LANGUAGE,
                                                                            HeaderNames.CACHE_CONTROL,
                                                                            HeaderNames.CONNECTION,
                                                                            HeaderNames.CONTENT_ENCODING,
                                                                            HeaderNames.CONTENT_LANGUAGE,
                                                                            HeaderNames.CONTENT_LENGTH,
                                                                            HeaderNames.CONTENT_TYPE,
                                                                            HeaderNames.DATE,
                                                                            HeaderNames.EXPECT,
                                                                            HeaderNames.HOST,
                                                                            HeaderNames.SERVER,
                                                                            HeaderNames.TE,
                                                                            HeaderNames.TRAILER,
                                                                            HeaderNames.TRANSFER_ENCODING,
                                                                            HeaderNames.UPGRADE,
                                                                            HeaderNames.USER_AGENT,
                                                                            HeaderNames.VARY);

    private static final String REDACTED = "<redacted>";

    private final Set<HeaderName> safeHeaderNames;

    private LogFormatter(Set<HeaderName> safeHeaderNames) {
        this.safeHeaderNames = safeHeaderNames;
    }

    /**
     * Create a formatter.
     *
     * @param config HTTP log configuration
     * @return a new formatter
     */
    public static LogFormatter create(HttpLogConfig config) {
        return new LogFormatter(config.safeHeaders());
    }

    /**
     * Escape a string before including it in a log record.
     *
     * @param value value to escape
     * @return escaped value
     */
    public static String escape(String value) {
        Objects.requireNonNull(value, "value");

        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '\r' -> builder.append("\\r");
            case '\n' -> builder.append("\\n");
            case '\t' -> builder.append("\\t");
            case '\\' -> builder.append("\\\\");
            default -> {
                if (Character.isISOControl(ch)) {
                    builder.append("\\u");
                    String hex = Integer.toHexString(ch);
                    builder.repeat("0", 4 - hex.length());
                    builder.append(hex);
                } else {
                    builder.append(ch);
                }
            }
            }
        }
        return builder.toString();
    }

    /**
     * Remove query and fragment data from a request target before logging it.
     *
     * @param requestTarget request target, possibly containing query or fragment
     * @return path without query and fragment
     */
    public static String pathOnly(String requestTarget) {
        Objects.requireNonNull(requestTarget, "requestTarget");
        if (requestTarget.isEmpty()) {
            return "/";
        }

        int query = requestTarget.indexOf('?');
        int fragment = requestTarget.indexOf('#');
        int end = requestTarget.length();
        if (query > -1) {
            end = query;
        }
        if (fragment > -1 && fragment < end) {
            end = fragment;
        }
        return requestTarget.substring(0, end);
    }

    /**
     * Default header names whose values can be logged in protocol logs.
     *
     * @return default safe header names
     */
    static Set<HeaderName> defaultSafeHeaderNames() {
        return new LinkedHashSet<>(DEFAULT_SAFE_HEADER_NAMES);
    }

    /**
     * Format headers for protocol logs.
     * <p>
     * Header names are always included. Header values are included only for configured safe header names.
     * </p>
     *
     * @param headers headers to format
     * @return formatted headers
     */
    public String format(Headers headers) {
        Objects.requireNonNull(headers, "headers");
        StringBuilder builder = new StringBuilder();

        for (Header header : headers) {
            for (String value : header.allValues()) {
                append(builder, header.headerName(), value);
            }
        }

        return builder.toString();
    }

    /**
     * Format all header values for explicitly enabled unsafe protocol logs.
     * <p>
     * Header names and values are escaped, but values are not redacted.
     * </p>
     *
     * @param headers headers to format
     * @return formatted headers
     */
    public String formatAll(Headers headers) {
        Objects.requireNonNull(headers, "headers");
        StringBuilder builder = new StringBuilder();

        for (Header header : headers) {
            for (String value : header.allValues()) {
                append(builder, header.headerName(), value, true);
            }
        }

        return builder.toString();
    }

    private static boolean denied(HeaderName headerName) {
        String lowerCase = headerName.lowerCase();
        return headerName.equals(HeaderNames.AUTHORIZATION)
                || headerName.equals(HeaderNames.PROXY_AUTHORIZATION)
                || headerName.equals(HeaderNames.COOKIE)
                || headerName.equals(HeaderNames.SET_COOKIE)
                || headerName.equals(HeaderNames.SET_COOKIE2)
                || lowerCase.contains("token")
                || lowerCase.contains("password")
                || lowerCase.contains("secret")
                || lowerCase.contains("key");
    }

    private void append(StringBuilder builder, HeaderName headerName, String value) {
        Objects.requireNonNull(builder, "builder");
        Objects.requireNonNull(headerName, "headerName");
        Objects.requireNonNull(value, "value");
        append(builder, headerName, value, !denied(headerName) && safeHeaderNames.contains(headerName));
    }

    private void append(StringBuilder builder, HeaderName headerName, String value, boolean safe) {
        builder.append(escape(headerName.defaultCase()))
                .append(": ")
                .append(safe ? escape(value) : REDACTED)
                .append('\n');
    }
}
