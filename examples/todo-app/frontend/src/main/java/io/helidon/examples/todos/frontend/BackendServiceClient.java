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

package io.helidon.examples.todos.frontend;

import java.net.URI;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.http.Http.Status.Family;
import io.helidon.http.HttpException;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webclient.security.WebClientSecurity;
import io.helidon.webclient.tracing.WebClientTracing;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

/**
 * Client to invoke the backend service.
 */
final class BackendServiceClient {

    private final LazyValue<Http1Client> client = LazyValue.create(this::createClient);
    private final Supplier<URI> serviceEndpoint;

    BackendServiceClient(Supplier<URI> serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
    }

    private Http1Client createClient() {
        return Http1Client.builder()
                .servicesDiscoverServices(false)
                .addService(WebClientTracing.create())
                .addService(WebClientSecurity.create())
                .addMediaSupport(JsonpSupport.create())
                .baseUri(serviceEndpoint.get().resolve("/api/backend"))
                .build();
    }

    private Http1Client client() {
        return client.get();
    }

    /**
     * Retrieve all entries from the backend.
     *
     * @return single with all records
     */
    JsonArray list() {
        return processResponse(client().get().request(), JsonArray.class);
    }

    /**
     * Retrieve the entry identified by the given ID.
     *
     * @param id the ID identifying the entry to retrieve
     * @return retrieved entry as a {@code JsonObject}
     */
    JsonObject get(String id) {
        return processResponse(client().get(id).request(), JsonObject.class);
    }

    /**
     * Delete the entry identified by the given ID.
     *
     * @param id the ID identifying the entry to delete
     * @return deleted entry as a {@code JsonObject}
     */
    JsonObject deleteSingle(String id) {
        return processResponse(client().delete(id).request(), JsonObject.class);
    }

    /**
     * Create a new entry.
     *
     * @param json the new entry value to create as {@code JsonObject}
     * @return created entry as {@code JsonObject}
     */
    JsonObject create(JsonObject json) {
        return processResponse(client().post().submit(json), JsonObject.class);
    }

    /**
     * Update an entry identified by the given ID.
     *
     * @param id   the ID identifying the entry to update
     * @param json the update entry value as {@code JsonObject}
     * @return updated entry as {@code JsonObject}
     */
    JsonObject update(String id, JsonObject json) {
        return processResponse(client().put(id).submit(json), JsonObject.class);
    }

    private <T> T processResponse(Http1ClientResponse response, Class<T> clazz) {
        if (response.status().family() != Family.SUCCESSFUL) {
            throw new HttpException("backend error", response.status());
        }
        return response.entity().as(clazz);
    }
}
