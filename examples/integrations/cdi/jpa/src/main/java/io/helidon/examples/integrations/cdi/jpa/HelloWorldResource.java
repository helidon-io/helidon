/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
