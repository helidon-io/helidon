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

    # The following commands download the generator and run it to generate projects containing the types which need to be
    # mocked in the snippets in case of changes in the upstream generators.
    #
    # See these instructions https://github.com/OpenAPITools/openapi-generator?tab=readme-ov-file#13---download-jar for full
    # instructions to download the generator and
    # https://github.com/OpenAPITools/openapi-generator?tab=readme-ov-file#11---compatibility to see the latest stable version
    # of the generator.
    #
    # As of this writing use at least 7.8.0.
    #
    # Revise the generatorVersion and helidonVersion settings below accordingly.

    generatorVersion=7.8.0
    helidonVersion=4 # Uses the latest publicly released version of 4. Specify x.y.z if you want to specify an exact release.

    curl -O \
        --output-dir /tmp \
        https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/$generatorVersion/openapi-generator-cli-${generatorVersion}.jar

    rm -rf /tmp/petapi-client
    java -jar /tmp/openapi-generator-cli-$generatorVersion.jar generate \
           -o /tmp/petapi-client \
           -g java-helidon-client \
           --library mp \
           -i etc/petstorex.yaml \
           -p helidonVersion=${helidonVersion}

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