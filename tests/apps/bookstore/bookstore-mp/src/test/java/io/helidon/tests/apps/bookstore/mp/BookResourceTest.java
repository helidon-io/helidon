/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.apps.bookstore.mp;

import io.helidon.tests.apps.bookstore.common.Book;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import io.helidon.microprofile.server.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookResourceTest {

    private static Server server;

    private static Client client = ClientBuilder.newClient();

    @BeforeAll
    public static void startTheServer() {
        server = Main.startServer();
    }

    @Test
    void testBooks() {
        assertBookStoreSize(0);

        Response res = client.target(getConnectionString("/books"))
                .request()
                .post(Entity.json(getBookAsJson()));
        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());

        assertBookStoreSize(1);

        res = client.target(getConnectionString("/books/123456"))
                .request()
                .get();
        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());
        assertNotNull(res.getHeaderString("content-length"));

        assertBookStoreSize(1);

        res = client.target(getConnectionString("/books/123456"))
                .request()
                .put(Entity.json(getBookAsJson()));
        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());

        assertBookStoreSize(1);

        res = client.target(getConnectionString("/books/123456"))
                .request()
                .delete();
        assertEquals(Response.Status.OK.getStatusCode(), res.getStatus());

        assertBookStoreSize(0);
    }

    @AfterAll
    static void destroyClass() {
        CDI<Object> current = CDI.current();
        ((SeContainer) current).close();
    }

    private String getConnectionString(String path) {
        return "http://localhost:" + server.port() + path;
    }

    private String getBookAsJson() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("book.json");
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    private void assertBookStoreSize(int size) {
        Book[] jsonArray = client
                .target(getConnectionString("/books"))
                .request()
                .get(Book[].class);
        assertEquals(size, jsonArray.length);
    }
}
