/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.tests.apps.bookstore.common.Book;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsArrayWithSize.arrayWithSize;

@HelidonTest
class BookResourceTest {

    @Inject
    private WebTarget webTarget;

    @Test
    void testBooks() {
        assertBookStoreSize(0);

        Response res = webTarget.path("/books")
                .request()
                .post(Entity.json(getBookAsJson()));
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertBookStoreSize(1);

        res = webTarget.path("/books/123456")
                .request()
                .get();
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(res.getHeaderString("content-length"), notNullValue());

        assertBookStoreSize(1);

        res = webTarget.path("/books/123456")
                .request()
                .put(Entity.json(getBookAsJson()));
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertBookStoreSize(1);

        res = webTarget.path("/books/123456")
                .request()
                .delete();
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertBookStoreSize(0);
    }

    @Test
    void testBooks2() {
        assertBookStoreSize(0);

        Response res = webTarget.path("/books2")
                .request()
                .post(Entity.json(getBookAsJson()));
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertBookStoreSize(1);

        res = webTarget.path("/books2/123456")
                .request()
                .get();
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(res.getHeaderString("content-length"), notNullValue());

        assertBookStoreSize(1);

        res = webTarget.path("/books2/123456")
                .request()
                .put(Entity.json(getBookAsJson()));
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertBookStoreSize(1);

        res = webTarget.path("/books2/123456")
                .request()
                .delete();
        assertThat(res.getStatus(), is(Response.Status.OK.getStatusCode()));

        assertBookStoreSize(0);
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
        Book[] jsonArray = webTarget.path("/books")
                .request()
                .get(Book[].class);
        assertThat(jsonArray, arrayWithSize(size));
    }
}
