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
package io.helidon.data.jdbc.tests.mixed.provider;

import io.helidon.common.Api;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.data.Data;
import io.helidon.data.jdbc.JdbcClient;
import io.helidon.http.Http;
import io.helidon.service.registry.Service;
import io.helidon.webserver.http.RestServer;

/**
 * HTTP facade exercising both repository providers and both JDBC API styles.
 */
@SuppressWarnings(Api.SUPPRESS_INCUBATING)
@Http.Path("/mixed")
@Service.Singleton
@RestServer.Endpoint
class MixedProviderEndpoint {

    private final JpaBookRepository books;
    private final JdbcInventoryRepository inventory;
    private final JdbcClient client;

    @Service.Inject
    MixedProviderEndpoint(JpaBookRepository books,
                          JdbcInventoryRepository inventory,
                          @Data.ProviderType("jdbc") @Service.Named("mixed") JdbcClient client) {
        this.books = books;
        this.inventory = inventory;
        this.client = client;
    }

    @Http.GET
    @Http.Path("/jpa/{id}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    BookResponse jpa(@Http.PathParam("id") int id) {
        JpaBook book = books.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No JPA book with id " + id));
        return new BookResponse(book.getId(), book.getTitle());
    }

    @Http.GET
    @Http.Path("/jdbc/declarative/{id}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    InventoryResponse jdbcDeclarative(@Http.PathParam("id") int id) {
        JdbcInventory item = inventory.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No JDBC inventory item with id " + id));
        return response("declarative", item);
    }

    @Http.GET
    @Http.Path("/jdbc/imperative/{id}")
    @Http.Produces(MediaTypes.APPLICATION_JSON_VALUE)
    InventoryResponse jdbcImperative(@Http.PathParam("id") int id) {
        JdbcInventory item = client.create(
                        "SELECT ID, NAME, AVAILABLE FROM JDBC_INVENTORY WHERE ID = ?")
                .bind(1, id)
                .map(row -> new JdbcInventory(row.get("ID", Integer.class),
                                              row.get("NAME", String.class),
                                              row.get("AVAILABLE", Integer.class)))
                .one();
        return response("imperative", item);
    }

    private static InventoryResponse response(String access, JdbcInventory item) {
        return new InventoryResponse(access, item.id(), item.name(), item.available());
    }
}
