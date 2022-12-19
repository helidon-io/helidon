/*
 * OpenAPI Helidon Quickstart
 * This is a sample for Helidon Quickstart project.
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

package org.openapitools.server.api;

import org.openapitools.server.model.Message;

import javax.ws.rs.*;

import java.io.InputStream;
import java.util.Map;
import java.util.List;
import javax.validation.constraints.*;
import javax.validation.Valid;

@Path("/greet")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaHelidonServerCodegen", date = "2022-12-19T17:22:28.508111889+01:00[Europe/Prague]")
public interface MessageService {

    @GET
    @Produces({ "application/json" })
    Message getDefaultMessage();

    @GET
    @Path("/{name}")
    @Produces({ "application/json" })
    Message getMessage(@PathParam("name") String name);

    @PUT
    @Path("/greeting")
    @Consumes({ "application/json" })
    void updateGreeting(@Valid @NotNull Message message);
}
