/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common;

import java.net.URI;
import java.time.Duration;

import io.helidon.common.parameters.Parameters;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;

import jakarta.json.JsonObject;

/**
 * Oracle IDCS specific implementations for {@code idcs} server type.
 */
class IdcsSupport {
    // prevent instantiation
    private IdcsSupport() {
    }

    // load signature jwk with a token, blocking operation
    static JwkKeys signJwk(WebClient appWebClient,
                           WebClient generalClient,
                           URI tokenEndpointUri,
                           URI signJwkUri,
                           Duration clientTimeout,
                           TenantConfig tenantConfig) {
        //  need to get token to be able to request this endpoint
        Parameters.Builder formBuilder = Parameters.builder("idcs-form-params")
                .add("scope", "urn:opc:idm:__myscopes__");

        if (tenantConfig.tokenEndpointAuthentication() == OidcConfig.ClientAuthentication.CLIENT_CERTIFICATE) {
            formBuilder.add("grant_type", "tls_client_auth")
                    .add("client_id", tenantConfig.clientId());
        } else {
            formBuilder.add("grant_type", "client_credentials");
        }
        Parameters form = formBuilder.build();

        try (HttpClientResponse response = appWebClient.post()
                .uri(tokenEndpointUri)
                .header(HeaderValues.ACCEPT_JSON)
                .submit(form)) {

            if (response.status().family() == Status.Family.SUCCESSFUL) {
                JsonObject json = response.as(JsonObject.class);

                String accessToken = json.getString("access_token");

                // get the jwk from server
                JsonObject jwkJson = generalClient.get()
                        .uri(signJwkUri)
                        .header(HeaderNames.AUTHORIZATION, "Bearer " + accessToken)
                        .requestEntity(JsonObject.class);

                return JwkKeys.create(jwkJson);
            } else {
                String errorEntity = response.as(String.class);
                throw new SecurityException("Failed to read JWK from IDCS. Status: " + response.status()
                                                    + ", entity: " + errorEntity);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Failed to read JWK from IDCS", e);
        }
    }
}
