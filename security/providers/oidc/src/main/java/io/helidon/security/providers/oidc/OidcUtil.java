/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

import io.helidon.common.http.FormParams;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;

class OidcUtil {

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

    static void updateRequest(OidcConfig.RequestType type, TenantConfig tenantConfig, FormParams.Builder form) {
        if (type == OidcConfig.RequestType.CODE_TO_TOKEN
                && tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_SECRET_POST) {
            form.add("client_id", tenantConfig.clientId());
            form.add("client_secret", tenantConfig.clientSecret());
        }
    }

}
