/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.helidon.tests.apps.bookstore.common.Book;

/**
 * This class exists only to verify inheritance of {@link Path} in
 * this application. This is an extension to the JAX-RS spec supported
 * by Jersey.
 */
@Path("/books")
public abstract class BookResourceBase {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    abstract public Response getBooks();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    abstract public Response postBook(Book book);

}
