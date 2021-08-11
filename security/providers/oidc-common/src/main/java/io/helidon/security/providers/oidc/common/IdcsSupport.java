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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.json.JsonObject;

import io.helidon.common.Errors;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.security.SecurityException;
import io.helidon.security.jwt.jwk.JwkKeys;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.WebClientResponse;

/**
 * Oracle IDCS specific implementations for {@code idcs} server type.
 */
class IdcsSupport {
    // prevent instantiation
    private IdcsSupport() {
    }
    // load signature jwk with a token
    static JwkKeys signJwk(WebClient generalClient, URI tokenEndpointUri, Errors.Collector collector, URI signJwkUri) {
        //  need to get token to be able to request this endpoint
        FormParams form = FormParams.builder()
                .add("grant_type", "client_credentials")
                .add("scope", "urn:opc:idm:__myscopes__")
                .build();

        try {
            WebClientResponse response = generalClient.post()
                    .accept(MediaType.APPLICATION_JSON)
                    .submit(form)
                    .await();

            if (response.status().family() == Http.ResponseStatus.Family.SUCCESSFUL) {
                JsonObject json = response.content()
                        .as(JsonObject.class)
                        .await();

                String accessToken = json.getString("access_token");

                // get the jwk from server
                JsonObject jwkJson = generalClient.target(signJwkUri)
                        .request()
                        .header("Authorization", "Bearer " + accessToken)
                        .get(JsonObject.class);

                return JwkKeys.create(jwkJson);
            } else {
                String errorEntity = response.content()
                        .as(String.class)
                        .await();
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Failed to read JWK from IDCS", e);
        }
    }

    static Single<Void> processJsonSubmit(WebClientRequestBuilder request,
                                          Object toSubmit,
                                          Consumer<JsonObject> jsonProcessor,
                                          BiConsumer<Http.ResponseStatus, String> errorEntityProcessor,
                                          BiConsumer<Throwable, String> errorProcessor) {

        return request.submit(toSubmit)
                .peek(response -> {
                    if (response.status().family() == Http.ResponseStatus.Family.SUCCESSFUL) {
                        response.content()
                                .as(JsonObject.class)
                                .forSingle(jsonProcessor)
                                .exceptionallyAccept(t -> errorProcessor.accept(t,
                                                                                "Failed to read JSON from IDCS JWK request."));
                    } else {
                        response.content()
                                .as(String.class)
                                .forSingle(it -> errorEntityProcessor.accept(response.status(), it))
                                .exceptionallyAccept(t -> errorProcessor.accept(t,
                                                                                "Failed to read error entity from "
                                                                                        + "IDCS JWK response."));
                    }
                })
                .onErrorResumeWithSingle(t -> {
                    errorProcessor.accept(t, "Failed to invoke IDCS JWK request");
                    return Single.empty();
                })
                .flatMapSingle(it -> Single.empty());
    }
}
