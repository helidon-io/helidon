/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

import io.helidon.common.http.Http;
import io.helidon.common.parameters.Parameters;
import io.helidon.nima.webclient.api.HttpClientResponse;
import io.helidon.nima.webclient.api.WebClient;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.jwk.JwkKeys;

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
                           Duration clientTimeout) {
        //  need to get token to be able to request this endpoint
        Parameters form = Parameters.builder("idcs-form-params")
                .add("grant_type", "client_credentials")
                .add("scope", "urn:opc:idm:__myscopes__")
                .build();

        try (HttpClientResponse response = appWebClient.post()
                .uri(tokenEndpointUri)
                .header(Http.Headers.ACCEPT_JSON)
                .submit(form)) {

            if (response.status().family() == Http.Status.Family.SUCCESSFUL) {
                JsonObject json = response.as(JsonObject.class);

                String accessToken = json.getString("access_token");

                // get the jwk from server
                JsonObject jwkJson = generalClient.get()
                        .uri(signJwkUri)
                        .header(Http.HeaderNames.AUTHORIZATION, "Bearer " + accessToken)
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
