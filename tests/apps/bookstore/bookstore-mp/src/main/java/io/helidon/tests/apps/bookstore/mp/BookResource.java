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
import io.helidon.tests.apps.bookstore.common.BookStore;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;

@Path("/books")
@RequestScoped
public class BookResource {

    private final BookStore bookStore;

    @Inject
    public BookResource(BookStore bookStore) {
        this.bookStore = bookStore;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBooks() {
        Collection<Book> books = bookStore.getAll();
        return Response.ok(books).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
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
