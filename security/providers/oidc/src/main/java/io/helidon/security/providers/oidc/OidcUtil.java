/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.context.Contexts;
import io.helidon.common.parameters.Parameters;
import io.helidon.common.uri.UriValidator;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpException;
import io.helidon.http.UnauthorizedException;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.webclient.api.HttpClientRequest;
import io.helidon.webserver.http.ServerRequest;

class OidcUtil {
    private static final boolean WEB_SERVER_AVAILABLE = webServerAvailable();

    private OidcUtil() {
    }

    static Optional<String> localRedirectUri(String uri) {
        if (uri == null || uri.isEmpty() || uri.charAt(0) != '/' || uri.startsWith("//") || uri.indexOf('\\') >= 0) {
            // Reject missing, non-root-relative, scheme-relative, or backslash-containing values.
            return Optional.empty();
        }
        String lowerCaseUri = uri.toLowerCase(Locale.ROOT);
        if (lowerCaseUri.startsWith("/%2f") || lowerCaseUri.startsWith("/%5c")) {
            // Reject encoded slash or backslash prefixes that could become non-local after decoding.
            return Optional.empty();
        }
        for (int i = 0; i < uri.length(); i++) {
            if (Character.isISOControl(uri.charAt(i))) {
                // Reject control characters that could alter redirect headers or parsing.
                return Optional.empty();
            }
        }
        try {
            URI parsedUri = URI.create(uri);
            if (parsedUri.isAbsolute() || parsedUri.getRawAuthority() != null) {
                // Reject absolute and authority-bearing URIs; redirects must stay on this origin.
                return Optional.empty();
            }
        } catch (IllegalArgumentException e) {
            // Reject values that cannot be parsed as a URI.
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    static URI requestedUri(SecurityEnvironment environment) {
        // Do not link ServerRequest-dependent lambdas when the optional WebServer dependency is absent.
        return WEB_SERVER_AVAILABLE ? WebServerContext.requestedUri(environment) : environment.targetUri();
    }

    static String frontendUri(OidcConfig oidcConfig, TenantConfig tenantConfig, Supplier<URI> requestedUri) {
        Optional<String> configuredFrontendUri = oidcConfig.frontendUri();
        if (configuredFrontendUri.isPresent()) {
            return configuredFrontendUri.get();
        }

        URI uri;
        try {
            uri = requestedUri.get();
        } catch (IllegalArgumentException | HttpException e) {
            throw frontendUriFailure(tenantConfig, "Requested URI does not have a valid HTTP origin", e);
        }
        String origin = normalizedOrigin(uri, oidcConfig.forceHttpsRedirects());
        if (origin == null || !oidcConfig.frontendUriAllowList().test(origin)) {
            throw frontendUriFailure(tenantConfig,
                                     "Frontend URI is not configured and requested URI origin is not allowed",
                                     null);
        }
        return origin;
    }

    static void updateRequest(OidcConfig.RequestType type,
                              TenantConfig tenantConfig,
                              Parameters.Builder form,
                              HttpClientRequest request) {
        if (type == OidcConfig.RequestType.CODE_TO_TOKEN || type == OidcConfig.RequestType.ID_AND_SECRET_TO_TOKEN) {
            if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_POST) {
                form.add("client_id", tenantConfig.clientId());
                form.add("client_secret", tenantConfig.clientSecret());
            } else if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_CERTIFICATE) {
                if (tenantConfig.serverType().equals("idcs")) {
                    //IDCS needs to have Authorization header included to properly pass.
                    String toEncode = tenantConfig.clientId() + ":" + tenantConfig.clientSecret();
                    String encoded = Base64.getEncoder().encodeToString(toEncode.getBytes(StandardCharsets.UTF_8));
                    request.header(HeaderNames.AUTHORIZATION, "Basic " + encoded);
                } else {
                    form.add("client_id", tenantConfig.clientId());
                }
            }
        }
    }

    private static UnauthorizedException frontendUriFailure(TenantConfig tenantConfig, String message, Throwable cause) {
        UnauthorizedException failure = new UnauthorizedException(message, cause);
        String realm = tenantConfig.realm().replace("\\", "\\\\").replace("\"", "\\\"");
        failure.header(HeaderValues.create(HeaderNames.WWW_AUTHENTICATE, "Bearer realm=\"" + realm + "\""));
        return failure;
    }

    private static boolean webServerAvailable() {
        try {
            Class.forName("io.helidon.webserver.http.ServerRequest", false, OidcUtil.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static String normalizedOrigin(URI uri, boolean forceHttps) {
        if (uri == null || uri.getScheme() == null || uri.getRawUserInfo() != null) {
            return null;
        }
        String requestedScheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(requestedScheme) && !"https".equals(requestedScheme)) {
            return null;
        }
        String scheme = forceHttps ? "https" : requestedScheme;
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            String normalizedHost = normalizedIpv6(host);
            if (normalizedHost == null) {
                return null;
            }
            host = normalizedHost;
        } else {
            try {
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException e) {
                return null;
            }
            if (isIpv4AddressLike(host)) {
                String[] segments = host.split("\\.", -1);
                if (segments.length != 4) {
                    return null;
                }
                for (String segment : segments) {
                    if (segment.isEmpty()
                            || segment.length() > 3
                            || segment.length() > 1 && segment.charAt(0) == '0'
                            || Integer.parseInt(segment) > 255) {
                        return null;
                    }
                }
            }
        }

        int port = uri.getPort();
        if (port < -1 || port == 0 || port > 65535) {
            return null;
        }
        // Discovery exposes an effective port, not whether it was explicit in the request.
        if (forceHttps && "http".equals(requestedScheme) && port == 80) {
            port = -1;
        }
        if ((port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme))) {
            port = -1;
        }
        return scheme + "://" + host + (port == -1 ? "" : ":" + port);
    }

    private static boolean isIpv4AddressLike(String host) {
        String ipv4Candidate = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        String[] segments = ipv4Candidate.split("\\.", -1);
        if (segments.length > 4) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty()) {
                return false;
            }
            int firstDigit = segment.startsWith("0x") ? 2 : 0;
            if (firstDigit == segment.length()) {
                return false;
            }
            for (int i = firstDigit; i < segment.length(); i++) {
                char character = segment.charAt(i);
                if (firstDigit == 0 ? !Character.isDigit(character) : Character.digit(character, 16) == -1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String normalizedIpv6(String host) {
        String addressText = host.substring(1, host.length() - 1);
        if (addressText.indexOf('%') >= 0 || addressText.indexOf(':') < 0) {
            return null;
        }
        try {
            // Validate embedded IPv4 octets before InetAddress parses a mixed IPv6 literal.
            UriValidator.validateIpLiteral(host);
            InetAddress address = InetAddress.getByName(addressText);
            byte[] addressBytes = address.getAddress();
            if (!(address instanceof Inet6Address)) {
                byte[] mappedAddress = new byte[16];
                mappedAddress[10] = (byte) 0xff;
                mappedAddress[11] = (byte) 0xff;
                System.arraycopy(addressBytes, 0, mappedAddress, 12, addressBytes.length);
                addressBytes = mappedAddress;
            }
            return '[' + normalizedIpv6(addressBytes) + ']';
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String normalizedIpv6(byte[] address) {
        int[] segments = new int[8];
        for (int i = 0; i < segments.length; i++) {
            segments[i] = Byte.toUnsignedInt(address[i * 2]) << 8
                    | Byte.toUnsignedInt(address[i * 2 + 1]);
        }

        int longestZeroStart = -1;
        int longestZeroLength = 1;
        for (int i = 0; i < segments.length;) {
            if (segments[i] != 0) {
                i++;
                continue;
            }
            int zeroStart = i;
            while (i < segments.length && segments[i] == 0) {
                i++;
            }
            int zeroLength = i - zeroStart;
            if (zeroLength > longestZeroLength) {
                longestZeroStart = zeroStart;
                longestZeroLength = zeroLength;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.length;) {
            if (i == longestZeroStart) {
                result.append("::");
                i += longestZeroLength;
            } else {
                if (!result.isEmpty() && result.charAt(result.length() - 1) != ':') {
                    result.append(':');
                }
                result.append(Integer.toHexString(segments[i++]));
            }
        }
        return result.toString();
    }

    private static final class WebServerContext {
        private WebServerContext() {
        }

        private static URI requestedUri(SecurityEnvironment environment) {
            // Jersey exposes ServerRequest in its context; its JAX-RS URI can still contain the raw authority.
            // SE security environments already obtain targetUri from ServerRequest.requestedUri().
            return Contexts.context()
                    .flatMap(context -> context.get(ServerRequest.class))
                    .map(request -> request.requestedUri().toUri())
                    .orElseGet(environment::targetUri);
        }
    }
}
