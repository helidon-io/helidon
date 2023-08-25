/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.demo.todos.frontend;

import java.util.function.Function;

import io.helidon.common.http.Http.ResponseStatus.Family;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webclient.tracing.WebClientTracing;
import io.helidon.webserver.HttpException;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Client to invoke the backend service.
 */
final class BackendServiceClient {

    private final WebClient client;

    BackendServiceClient(Config config) {
        String serviceEndpoint = config.get("services.backend.endpoint").asString().get();
        this.client = WebClient.builder()
                .useSystemServiceLoader(false)
                .addService(WebClientTracing.create())
                .addService(WebClientSecurity.create())
                .addMediaSupport(JsonpSupport.create())
                .baseUri(serviceEndpoint + "/api/backend").build();
    }

    /**
     * Retrieve all entries from the backend.
     *
     * @return single with all records
     */
    Single<JsonArray> list() {
        return client.get()
                .request()
                .flatMapSingle(processResponse(JsonArray.class));
    }

    /**
     * Retrieve the entry identified by the given ID.
     *
     * @param id the ID identifying the entry to retrieve
     * @return retrieved entry as a {@code JsonObject}
     */
    Single<JsonObject> get(String id) {
        return client.get()
                .path(id)
                .request()
                .flatMapSingle(processResponse(JsonObject.class));
    }

    /**
     * Delete the entry identified by the given ID.
     *
     * @param id the ID identifying the entry to delete
     * @return deleted entry as a {@code JsonObject}
     */
    Single<JsonObject> deleteSingle(String id) {
        return client.delete()
                .path(id)
                .request()
                .flatMapSingle(processResponse(JsonObject.class));
    }

    /**
     * Create a new entry.
     *
     * @param json the new entry value to create as {@code JsonObject}
     * @return created entry as {@code JsonObject}
     */
    Single<JsonObject> create(JsonObject json) {
        return client.post()
                .submit(json)
                .flatMapSingle(processResponse(JsonObject.class));
    }

    /**
     * Update an entry identified by the given ID.
     *
     * @param id   the ID identifying the entry to update
     * @param json the update entry value as {@code JsonObject}
     * @return updated entry as {@code JsonObject}
     */
    Single<JsonObject> update(String id, JsonObject json) {
        return client.put()
                .path(id)
                .submit(json)
                .flatMapSingle(processResponse(JsonObject.class));
    }

    private <T> Function<WebClientResponse, Single<T>> processResponse(Class<T> clazz) {
        return response -> {
            if (response.status().family() != Family.SUCCESSFUL) {
                return Single.error(new HttpException("backend error", response.status()));
            }
            return response.content().as(clazz);
        };
    }
}
