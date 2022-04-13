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

import java.util.Collection;

import io.helidon.tests.apps.bookstore.common.Book;
import io.helidon.tests.apps.bookstore.common.BookStore;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.helidon.tests.apps.bookstore.common.Book;
import io.helidon.tests.apps.bookstore.common.BookStore;

/**
 * The {@link Path} annotation is inherited from the base class. Note that a
 * CDI scope annotation such as {@code @RequestScoped} is required given that
 * discovery mode for this application is annotated.
 */
@RequestScoped
public class BookResource extends BookResourceBase {

    private final BookStore bookStore;

    @Inject
    public BookResource(BookStore bookStore) {
        this.bookStore = bookStore;
    }

    @Override
    public Response getBooks() {
        Collection<Book> books = bookStore.getAll();
        return Response.ok(books).build();
    }

    @Override
    public Response postBook(Book book) {
        if (bookStore.contains(book.getIsbn())) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        bookStore.store(book);
        return Response.ok().build();
    }

    @GET
    @Path("{isbn}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBook(@PathParam("isbn") String isbn) {
        Book book = bookStore.find(isbn);
        if (book == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok(book).build();
        }
    }

    @PUT
    @Path("{isbn}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response putBook(Book book) {
        if (!bookStore.contains(book.getIsbn())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        bookStore.store(book);
        return Response.ok().build();
    }

    @DELETE
    @Path("{isbn}")
    public Response deleteBook(@PathParam("isbn") String isbn) {
        if (!bookStore.contains(isbn)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        bookStore.remove(isbn);
        return Response.ok().build();
    }
}
