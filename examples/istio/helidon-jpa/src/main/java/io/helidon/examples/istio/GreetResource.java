/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.StoredProcedureQuery;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

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

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    @PersistenceContext(unitName = "hello")
    private EntityManager em;

    @Inject
    @RestClient
    private GreetingProvider greetingProvider;

    /**
     * Default Constructor
     */
    public GreetResource() {
    }

    /**
     * Return a worldly greeting message.
     *
     * @return {@link JsonObject}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        return createResponse("World");
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param nick the nick to greet
     * @return {@link JsonObject}
     */
    @Path("/{nick}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getMessage(@PathParam("nick") String nick) {
        Person entity = em.find(Person.class, nick);
        JsonObjectBuilder entityBuilder = JSON.createObjectBuilder()
                .add("nick", nick);
        if (entity == null) {
            JsonObject responseEntity = entityBuilder
                    .add("error", String.format("Nick %s was not found", nick))
                    .build();
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(responseEntity)
                    .build();
        }
        JsonObject responseEntity = createResponse(entity.getName());
        return Response
                .status(Response.Status.OK)
                .entity(responseEntity)
                .build();
    }

    /**
     * Return all persons info, if available.
     *
     * @return {@link JsonObject}
     */
    @Path("/all")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response getAll() {

        StoredProcedureQuery getAllProcedure =
                em.createNamedStoredProcedureQuery("GetAllPersons");

        List<Person> persons = getAllProcedure.getResultList();

        if (persons == null || persons.isEmpty()) {
            JsonObject responseEntity = JSON.createObjectBuilder()
                    .add("error", String.format("No person was not found"))
                    .build();
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(responseEntity)
                    .build();
        }

        StringBuilder msg = new StringBuilder("The Persons are:");
        //Looping through the Resultant list
        for (Person person : persons) {
            System.out.println(person.toString());
            msg.append(person);
        }

        JsonObject responseEntity = JSON.createObjectBuilder()
                .add("message", msg.toString())
                .build();

        return Response
                .status(Response.Status.OK)
                .entity(responseEntity)
                .build();
    }

    /**
     * Store a new person for greetings.
     *
     * @param jsonPerson JSON object with person to store
     * @return HTTPrequest result
     */
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @POST
    public Response createPerson(JsonObject jsonPerson) {
        if (jsonPerson == null || !jsonPerson.containsKey("nick")
                || !jsonPerson.containsKey("name")) {
            return Response
                    .status(Response.Status.fromStatusCode(422))
                    .build();
        }
        String nick = jsonPerson.getString("nick");
        String name = jsonPerson.getString("name");
        Person person = new Person();
        person.setNick(nick);
        person.setName(name);
        JsonObjectBuilder entityBuilder = JSON.createObjectBuilder()
                .add("nick", nick)
                .add("name", name);
        try {
            em.persist(person);
            return Response.status(Response.Status.OK)
                    .entity(entityBuilder.build())
                    .build();
        } catch (PersistenceException pe) {
            pe.printStackTrace();
            JsonObject entity = entityBuilder
                    .add("error", pe.getMessage())
                    .build();
            return Response
                    .status(Response.Status.CONFLICT)
                    .entity(entity)
                    .build();
        }
    }

    private JsonObject createResponse(String who) {
        String msg = String.format("%s %s!", greetingProvider.getMessage(), who);

        return JSON.createObjectBuilder()
                .add("message", msg)
                .build();
    }

}
