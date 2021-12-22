/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.cdi.jpa;

import java.net.URI;
import java.util.Objects;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * A JAX-RS root resource class that manipulates greetings in a
 * database.
 *
 * @see #get(String)
 *
 * @see #post(String, String)
 */
@Path("")
@RequestScoped
public class HelloWorldResource {

    /**
     * The {@link EntityManager} used by this class.
     *
     * <p>Note that it behaves as though there is a transaction manager
     * in effect, because there is.</p>
     */
    @PersistenceContext(unitName = "test")
    private EntityManager entityManager;

    /**
     * A {@link Transaction} that is guaranteed to be non-{@code null}
     * only when a transactional method is executing.
     *
     * @see #post(String, String)
     */
    @Inject
    private Transaction transaction;

    /**
     * Creates a new {@link HelloWorldResource}.
     */
    public HelloWorldResource() {
        super();
    }

    /**
     * Returns a {@link Response} with a status of {@code 404} when
     * invoked.
     *
     * @return a non-{@code null} {@link Response}
     */
    @GET
    @Path("favicon.ico")
    public Response getFavicon() {
        return Response.status(404).build();
    }

    /**
     * When handed a {@link String} like, say, "{@code hello}", responds
     * with the second part of the composite greeting as found via an
     * {@link EntityManager}.
     *
     * @param firstPart the first part of the greeting; must not be
     * {@code null}
     *
     * @return the second part of the greeting; never {@code null}
     *
     * @exception NullPointerException if {@code firstPart} was {@code
     * null}
     *
     * @exception PersistenceException if the {@link EntityManager}
     * encountered an error
     */
    @GET
    @Path("{firstPart}")
    @Produces(MediaType.TEXT_PLAIN)
    public String get(@PathParam("firstPart") final String firstPart) {
        Objects.requireNonNull(firstPart);
        assert this.entityManager != null;
        final Greeting greeting = this.entityManager.find(Greeting.class, firstPart);
        assert greeting != null;
        return greeting.toString();
    }

    /**
     * When handed two parts of a greeting, like, say, "{@code hello}"
     * and "{@code world}", stores a new {@link Greeting} entity in the
     * database appropriately.
     *
     * @param firstPart the first part of the greeting; must not be
     * {@code null}
     *
     * @param secondPart the second part of the greeting; must not be
     * {@code null}
     *
     * @return the {@link String} representation of the resulting {@link
     * Greeting}'s identifier; never {@code null}
     *
     * @exception NullPointerException if {@code firstPart} or {@code
     * secondPart} was {@code null}
     *
     * @exception PersistenceException if the {@link EntityManager}
     * encountered an error
     *
     * @exception SystemException if something went wrong with the
     * transaction
     */
    @POST
    @Path("{firstPart}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Transactional(TxType.REQUIRED)
    public Response post(@PathParam("firstPart") final String firstPart,
                         final String secondPart)
        throws SystemException {
        Objects.requireNonNull(firstPart);
        Objects.requireNonNull(secondPart);
        assert this.transaction != null;
        assert this.transaction.getStatus() == Status.STATUS_ACTIVE;
        assert this.entityManager != null;
        assert this.entityManager.isJoinedToTransaction();
        Greeting greeting = this.entityManager.find(Greeting.class, firstPart);
        final boolean created;
        if (greeting == null) {
          greeting = new Greeting(firstPart, secondPart);
          this.entityManager.persist(greeting);
          created = true;
        } else {
          greeting.setSecondPart(secondPart);
          created = false;
        }
        assert this.entityManager.contains(greeting);
        if (created) {
            return Response.created(URI.create(firstPart)).build();
        } else {
            return Response.ok(firstPart).build();
        }
    }

}
