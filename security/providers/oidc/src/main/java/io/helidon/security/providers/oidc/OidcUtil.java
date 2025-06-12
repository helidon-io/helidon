/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.providers.oidc.common.TenantConfig;
import io.helidon.webclient.api.HttpClientRequest;

class OidcUtil {

    private OidcUtil() {
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

}
