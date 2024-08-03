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

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.inject.RestClient;

public class OpenApiGeneratorSnippets {

    /*
    To generate a project containing the types which need to be mocked--in case of changes in the upstream generator:

    1. Download the generator: see [these instructions](https://github.com/OpenAPITools/openapi-generator?tab=readme-ov-file#13---download-jar).
    2. `mkdir mpClient`
    3. `cd mpClient`
    4. ```java
       java -jar {downloadLocation}/openapi-generator-cli.jar generate \
       -g java-helidon-client \
       --library mp \
       -i {helidon-root}/docs/src/main/resources/petstorex.yaml \
       --helidonVersion 4
       ```
       or specify the appropriate Helidon major or exact version.
     */

    interface Pet {
    }

    interface PetApi {
        Pet getPetById(long petId);
    }

    class ApiException extends Exception {
    }

// tag::class-declaration[]
    @Path("/exampleServiceCallingService") // <1>
    public class ExampleOpenApiGenClientResource {
        @Inject // <2>
        @RestClient // <3>
        private PetApi petApi; // <4>

        @GET
        @Path("/getPet/{petId}")
        @Produces(MediaType.APPLICATION_JSON)
        public Pet getPetUsingId(@PathParam("petId") Long petId) throws ApiException {
            Pet pet = petApi.getPetById(petId); // <5>
            return pet;
        }
    }
// end::class-declaration[]

}