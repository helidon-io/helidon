/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.mp.openapi;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.OASModelReader;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Paths;

@SuppressWarnings("ALL")
class OpenapiSnippets {

    // stub
    class GreetingUpdateMessage {
    }

    // tag::snippet_1[]
    @Path("/greeting")
    @PUT
    @Operation(summary = "Set the greeting prefix",
               description = "Permits the client to set the prefix part of the greeting (\"Hello\")") // <1>
    @RequestBody( //<2>
                  name = "greeting",
                  description = "Conveys the new greeting prefix to use in building greetings",
                  content = @Content(
                          mediaType = "application/json",
                          schema = @Schema(implementation = GreetingUpdateMessage.class),
                          examples = @ExampleObject(
                                  name = "greeting",
                                  summary = "Example greeting message to update",
                                  value = "{\"greeting\": \"New greeting message\"}")))
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateGreeting(JsonObject jsonObject) {
        return Response.ok().build();
    }
    // end::snippet_1[]

    // tag::snippet_2[]
    public class SimpleAPIFilter implements OASFilter {

        @Override
        public PathItem filterPathItem(PathItem pathItem) {
            for (var methodOp : pathItem.getOperations().entrySet()) {
                if (SimpleAPIModelReader.DOOMED_OPERATION_ID
                        .equals(methodOp.getValue().getOperationId())) {
                    return null;
                }
            }
            return OASFilter.super.filterPathItem(pathItem);
        }
    }
    // end::snippet_2[]

    // tag::snippet_3[]

    /**
     * Defines two paths using the OpenAPI model reader mechanism, one that should
     * be suppressed by the filter class and one that should appear in the published
     * OpenAPI document.
     */
    public class SimpleAPIModelReader implements OASModelReader {

        /**
         * Path for the example endpoint added by this model reader that should be visible.
         */
        public static final String MODEL_READER_PATH = "/test/newpath";

        /**
         * Path for an endpoint that the filter should hide.
         */
        public static final String DOOMED_PATH = "/test/doomed";

        /**
         * ID for an endpoint that the filter should hide.
         */
        public static final String DOOMED_OPERATION_ID = "doomedPath";

        /**
         * Summary text for the endpoint.
         */
        public static final String SUMMARY = "A sample test endpoint from ModelReader";

        @Override
        public OpenAPI buildModel() {
            /*
             * Add two path items, one of which we expect to be removed by
             * the filter and a very simple one that will appear in the
             * published OpenAPI document.
             */
            PathItem newPathItem = OASFactory.createPathItem()
                    .GET(OASFactory.createOperation()
                                 .operationId("newPath")
                                 .summary(SUMMARY));
            PathItem doomedPathItem = OASFactory.createPathItem()
                    .GET(OASFactory.createOperation()
                                 .operationId(DOOMED_OPERATION_ID)
                                 .summary("This should become invisible"));
            OpenAPI openAPI = OASFactory.createOpenAPI();
            Paths paths = OASFactory.createPaths()
                    .addPathItem(MODEL_READER_PATH, newPathItem)
                    .addPathItem(DOOMED_PATH, doomedPathItem);
            openAPI.paths(paths);

            return openAPI;
        }
    }
    // end::snippet_3[]

}
