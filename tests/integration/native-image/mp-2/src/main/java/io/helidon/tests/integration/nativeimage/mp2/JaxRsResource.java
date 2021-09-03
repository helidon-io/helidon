/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.nativeimage.mp2;

import java.net.URI;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transaction;
import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

/**
 * A resource to test.
 */
@Path("/")
public class JaxRsResource {
    /**
     * The {@link javax.persistence.EntityManager} used by this class.
     *
     * <p>Note that it behaves as though there is a transaction manager
     * in effect, because there is.</p>
     */
    @PersistenceContext(unitName = "test")
    private EntityManager entityManager;

    /**
     * A {@link javax.transaction.Transaction} that is guaranteed to be non-{@code null}
     * only when a transactional method is executing.
     *
     * @see #post(String, String)
     */
    @Inject
    private Transaction transaction;

    /**
     * Test method that does not do any DB operations.
     * @return hello string
     */
    @GET
    public String hello() {
        return "Hello";
    }

    @GET
    @Path("/db/{firstPart}")
    @Transactional
    public String getFromDb(@PathParam("firstPart") String firstPart) {
        return String.valueOf(entityManager.find(GreetingEntity.class, firstPart));
    }

    @POST
    @Path("/db/{firstPart}")
    @Transactional(Transactional.TxType.REQUIRED)
    public Response post(@PathParam("firstPart") String firstPart, String secondPart) {
        GreetingEntity greeting;

        boolean created = false;

        greeting = this.entityManager.find(GreetingEntity.class, firstPart);

        if (greeting == null) {
            greeting = new GreetingEntity(firstPart, secondPart);
            entityManager.persist(greeting);
            created = true;
        } else {
            greeting.setSecondPart(secondPart);
        }

        if (created) {
            return Response.created(URI.create(firstPart)).build();
        } else {
            return Response.ok(firstPart).build();
        }
    }
}
