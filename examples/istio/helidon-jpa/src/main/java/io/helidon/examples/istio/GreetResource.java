/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.istio;

import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.StoredProcedureQuery;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * A simple JAX-RS resource to greet you.
 * Requires helidon-config microservice to be deployed.
 *
 * Examples:
 * <p>
 * Get default greeting message:
 * curl -X GET http://localhost:8080/greet
 * <p>
 * Get greeting message for Joe:
 * curl -X GET http://localhost:8080/greet/Joe
 * <p>
 * The message is returned as a JSON object.
 */
@Path("/greet")
@RequestScoped
public class GreetResource {
    @PersistenceContext(unitName = "hello")
    private EntityManager em;

    @Inject
    @RestClient
    private GreetingProvider greetingProvider;

    /**
     * Default Constructor.
     */
    public GreetResource() {
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link GreetingMessage}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public GreetingMessage getDefaultMessage() {
        return createResponse("World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param nick the nick to greet
     * @return {@link GreetingMessage}
     */
    @Path("/{nick}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getMessage(@PathParam("nick") String nick) {
        Person entity = em.find(Person.class, nick);
        if (entity == null) {
            GreetingMessage message = new GreetingMessage(String.format("Nick %s was not found", nick));
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(message)
                    .build();
        }
        GreetingMessage responseEntity = createResponse(entity.getName());
        return Response
                .status(Response.Status.OK)
                .entity(responseEntity)
                .build();
    }

    /**
     * Return all persons info, if available.
     *
     * @return {@link GreetingMessage}
     */
    @Path("/all")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getAll() {

        StoredProcedureQuery getAllProcedure =
                em.createNamedStoredProcedureQuery("GetAllPersons");

        List<Person> persons = getAllProcedure.getResultList();
        GreetingMessage message = new GreetingMessage();

        if (persons == null || persons.isEmpty()) {
            message.setMessage("No person was not found");
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(message)
                    .build();
        }

        StringBuilder msg = new StringBuilder("The Persons are:");
        //Looping through the Resultant list
        for (Person person : persons) {
            System.out.println(person.toString());
            msg.append(person);
        }
        message.setMessage(msg.toString());

        return Response
                .status(Response.Status.OK)
                .entity(message)
                .build();
    }

    /**
     * Store a new person for greetings.
     *
     * @param person Person to store
     * @return HTTPrequest result
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @POST
    public Response createPerson(Person person) {
        if (person == null || person.getNick() == null
                || person.getName() == null) {
            return Response
                    .status(Response.Status.fromStatusCode(422))
                    .build();
        }
        try {
            em.persist(person);
            return Response.status(Response.Status.OK)
                    .entity(person)
                    .build();
        } catch (PersistenceException pe) {
            pe.printStackTrace();
            GreetingMessage message = new GreetingMessage("Error: " + pe.getMessage());
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(message)
                    .build();
        }
    }

    private GreetingMessage createResponse(String who) {
        String msg = String.format("%s %s!", greetingProvider.getMessage(), who);

        return new GreetingMessage(msg);
    }

}
