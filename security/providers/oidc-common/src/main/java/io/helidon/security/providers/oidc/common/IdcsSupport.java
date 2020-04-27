/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.json.JsonObject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import io.helidon.common.Errors;
import io.helidon.security.jwt.jwk.JwkKeys;

/**
 * Oracle IDCS specific implementations for {@code idcs} server type.
 */
class IdcsSupport {
    // prevent instantiation
    private IdcsSupport() {
    }
    // load signature jwk with a token
    static JwkKeys signJwk(Client generalClient, WebTarget tokenEndpoint, Errors.Collector collector, URI signJwkUri) {
        //  need to get token to be able to request this endpoint
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.putSingle("grant_type", "client_credentials");
        formData.putSingle("scope", "urn:opc:idm:__myscopes__");

        JsonObject response = tokenEndpoint.request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(formData), JsonObject.class);
        String accessToken = response.getString("access_token");

        // get the jwk from server
        JsonObject jwkJson = generalClient.target(signJwkUri)
                .request()
                .header("Authorization", "Bearer " + accessToken)
                .get(JsonObject.class);

        return JwkKeys.create(jwkJson);
    }

}
