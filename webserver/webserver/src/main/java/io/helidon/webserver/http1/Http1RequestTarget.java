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

package io.helidon.webserver.http1;

import java.util.List;

import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriPathSegment;
import io.helidon.common.uri.UriValidator;
import io.helidon.http.DirectHandler.EventType;
import io.helidon.http.Headers;
import io.helidon.http.HttpPrologue;
import io.helidon.http.RequestException;
import io.helidon.http.Status;
import io.helidon.webserver.http.DirectTransportRequest;

final class Http1RequestTarget implements UriPath {
    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    private final String rawTarget;
    private final String scheme;
    private final String rawAuthority;
    private final int pathOffset;
    private final UriPath path;

    private boolean authorityParsed;
    private String authority;

    private Http1RequestTarget(String rawTarget,
                               String scheme,
                               String rawAuthority,
                               int pathOffset,
                               UriPath path) {
        this.rawTarget = rawTarget;
        this.scheme = scheme;
        this.rawAuthority = rawAuthority;
        this.pathOffset = pathOffset;
        this.path = path;
    }

    static Http1RequestTarget create(String rawTarget) {
        int schemeEnd = rawTarget.indexOf(':');
        String scheme = rawTarget.substring(0, schemeEnd);
        int componentStart = schemeEnd + 1;
        int pathOffset = rawTarget.length();
        String rawAuthority = null;
        if (componentStart + 1 < rawTarget.length()
                && rawTarget.charAt(componentStart) == '/'
                && rawTarget.charAt(componentStart + 1) == '/') {
            int authorityStart = componentStart + 2;
            pathOffset = rawTarget.indexOf('/', authorityStart);
            if (pathOffset == -1) {
                pathOffset = rawTarget.length();
            }
            rawAuthority = rawTarget.substring(authorityStart, pathOffset);
        } else if (componentStart < rawTarget.length() && rawTarget.charAt(componentStart) == '/') {
            pathOffset = componentStart;
        }
        return new Http1RequestTarget(rawTarget,
                                      scheme,
                                      rawAuthority,
                                      pathOffset,
                                      UriPath.create(rawTarget.substring(pathOffset)));
    }

    static boolean isAbsoluteForm(String requestTarget) {
        int colon = requestTarget.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        char first = requestTarget.charAt(0);
        if ((first < 'a' || first > 'z') && (first < 'A' || first > 'Z')) {
            return false;
        }
        for (int i = 1; i < colon; i++) {
            char c = requestTarget.charAt(i);
            if ((c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '+' || c == '-' || c == '.') {
                continue;
            }
            return false;
        }
        return true;
    }

    static RequestException invalid(HttpPrologue prologue, Headers headers, String message, Throwable cause) {
        return RequestException.builder()
                .type(EventType.BAD_REQUEST)
                .status(Status.BAD_REQUEST_400)
                .request(DirectTransportRequest.create(prologue, headers))
                .setKeepAlive(false)
                .message(message)
                .safeMessage(true)
                .cause(cause)
                .build();
    }

    String scheme() {
        return scheme;
    }

    String authority() {
        if (!authorityParsed) {
            authority = parseAuthority();
            authorityParsed = true;
        }
        return authority;
    }

    boolean hasAuthority() {
        return rawAuthority != null && !rawAuthority.isEmpty();
    }

    boolean hasUserInfo() {
        return rawAuthority != null && rawAuthority.indexOf('@') >= 0;
    }

    boolean httpScheme() {
        return HTTP_SCHEME.equalsIgnoreCase(scheme) || HTTPS_SCHEME.equalsIgnoreCase(scheme);
    }

    boolean httpsScheme() {
        return HTTPS_SCHEME.equalsIgnoreCase(scheme);
    }

    @Override
    public String rawPath() {
        return rawTarget;
    }

    @Override
    public String rawPathNoParams() {
        String rawPath = path.rawPath();
        String rawPathNoParams = path.rawPathNoParams();
        if (rawPath.length() == rawPathNoParams.length()) {
            return rawTarget;
        }
        return rawTarget.substring(0, pathOffset) + rawPathNoParams;
    }

    @Override
    public String path() {
        return path.path();
    }

    @Override
    public Parameters matrixParameters() {
        return path.matrixParameters();
    }

    @Override
    public UriPath absolute() {
        return this;
    }

    @Override
    public List<UriPathSegment> segments() {
        return path.segments();
    }

    @Override
    public void validate() {
        int componentStart = scheme.length() + 1;
        if (rawAuthority == null
                && pathOffset == rawTarget.length()
                && componentStart < rawTarget.length()) {
            validateComponent(rawTarget, componentStart, rawTarget.length(), true);
        }
        path.validate();
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof Http1RequestTarget that && rawTarget.equals(that.rawTarget);
    }

    @Override
    public int hashCode() {
        return rawTarget.hashCode();
    }

    @Override
    public String toString() {
        return rawTarget;
    }

    private static String validateAuthority(String authority) {
        int portStart = -1;
        String host;
        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket < 0) {
                throw new IllegalArgumentException("IP literal authority is missing a closing bracket");
            }
            host = authority.substring(0, closingBracket + 1);
            if (closingBracket + 1 < authority.length()) {
                if (authority.charAt(closingBracket + 1) != ':') {
                    throw new IllegalArgumentException("Unexpected content after IP literal authority");
                }
                portStart = closingBracket + 2;
            }
        } else {
            int portSeparator = authority.lastIndexOf(':');
            if (portSeparator >= 0) {
                if (authority.indexOf(':') != portSeparator) {
                    throw new IllegalArgumentException("IPv6 authority must use square brackets");
                }
                host = authority.substring(0, portSeparator);
                portStart = portSeparator + 1;
            } else {
                host = authority;
            }
        }
        UriValidator.validateHost(host);
        if (portStart < 0) {
            return authority;
        }
        if (portStart == authority.length()) {
            return authority.substring(0, portStart - 1);
        }
        int port = 0;
        for (int i = portStart; i < authority.length(); i++) {
            char c = authority.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Authority port must contain only digits");
            }
            port = port * 10 + c - '0';
            if (port > 65535) {
                throw new IllegalArgumentException("Authority port must be between 0 and 65535");
            }
        }
        return authority;
    }

    private static String canonicalAuthority(String authority) {
        if (authority.indexOf('%') < 0 || authority.startsWith("[")) {
            return authority;
        }
        int portDelimiter = authority.lastIndexOf(':');
        String rawHost = portDelimiter < 1 ? authority : authority.substring(0, portDelimiter);
        String canonicalHost = canonicalRegisteredName(rawHost);
        return portDelimiter < 1 ? canonicalHost : canonicalHost + authority.substring(portDelimiter);
    }

    private static String canonicalRegisteredName(String rawHost) {
        int firstPercent = rawHost.indexOf('%');
        if (firstPercent == -1) {
            return rawHost;
        }
        StringBuilder result = new StringBuilder(rawHost.length());
        result.append(rawHost, 0, firstPercent);
        for (int i = firstPercent; i < rawHost.length(); i++) {
            char c = rawHost.charAt(i);
            if (c == '%') {
                int decoded = Character.digit(rawHost.charAt(i + 1), 16) * 16
                        + Character.digit(rawHost.charAt(i + 2), 16);
                if (isUnreserved(decoded)) {
                    result.append((char) decoded);
                } else {
                    result.append('%')
                            .append(Character.toUpperCase(rawHost.charAt(i + 1)))
                            .append(Character.toUpperCase(rawHost.charAt(i + 2)));
                }
                i += 2;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean isUnreserved(int c) {
        return c >= 'a' && c <= 'z'
                || c >= 'A' && c <= 'Z'
                || c >= '0' && c <= '9'
                || c == '-'
                || c == '.'
                || c == '_'
                || c == '~';
    }

    private static boolean isSubDelimiter(int c) {
        return c == '!'
                || c == '$'
                || c == '&'
                || c == '\''
                || c == '('
                || c == ')'
                || c == '*'
                || c == '+'
                || c == ','
                || c == ';'
                || c == '=';
    }

    private static void validateComponent(String value, int start, int end, boolean pathComponent) {
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (c < 128
                    && (isUnreserved(c)
                            || isSubDelimiter(c)
                            || c == ':'
                            || pathComponent && (c == '@' || c == '/'))) {
                continue;
            }
            if (c == '%') {
                if (i + 2 >= end
                        || Character.digit(value.charAt(i + 1), 16) == -1
                        || Character.digit(value.charAt(i + 2), 16) == -1) {
                    throw new IllegalArgumentException("Invalid percent encoding in absolute request-target");
                }
                i += 2;
                continue;
            }
            throw new IllegalArgumentException("Invalid character in absolute request-target");
        }
    }

    private String parseAuthority() {
        if (rawAuthority == null || rawAuthority.isEmpty()) {
            return null;
        }
        int userInfoEnd = rawAuthority.lastIndexOf('@');
        if (userInfoEnd >= 0) {
            validateComponent(rawAuthority, 0, userInfoEnd, false);
        }
        return canonicalAuthority(validateAuthority(rawAuthority.substring(userInfoEnd + 1)));
    }
}
