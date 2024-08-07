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
package io.helidon.docs.se.openapi;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.http.Status;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("ALL") class OpenApiGeneratorSnippets {

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

    # Generate the server project.
    rm -rf /tmp/petapi-server
    java -jar /tmp/openapi-generator-cli-$generatorVersion.jar generate \
           -o /tmp/petapi-server \
           -g java-helidon-server \
           --library se \
           -i etc/petstorex.yaml \
           -p useAbstractClass=true \
           -p helidonVersion=${helidonVersion}

    # Generate the client project.
    rm -rf /tmp/petapi-client
    java -jar /tmp/openapi-generator-cli-$generatorVersion.jar generate \
           -o /tmp/petapi-client \
           -g java-helidon-client \
           --library se \
           -i etc/petstorex.yaml \
           -p helidonVersion=${helidonVersion}

     */
    // stub
    public class AddPetOpCustom extends PetService.AddPetOp {
    }

    // stub
    static class PetService {

        void handleAddPet(ServerRequest request, ServerResponse response, Pet pet) {
        }

        void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                  List<String> tags) {
        }

        AddPetOp createAddPetOp() {
            return null;
        }

        static class AddPetOp {

            Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
                return null;
            }

            record Response405() {

                static Response405.Builder builder() {
                    return new Builder();
                }

                static class Builder {
                    void send(ServerResponse response) {
                    }
                }
            }

            record Response200(Pet response) {

                static Response200.Builder builder() {
                    return new Builder();
                }

                static class Builder {
                    void send(ServerResponse response) {
                    }
                }
            }
        }

        static class FindPetsByTagsOp {

            record Response200() {

                static Response200.Builder builder() {
                    return new Response200.Builder();
                }

                static class Builder {
                    public void send(ServerResponse response) {
                    }

                    public Builder response(List<Pet> result) {
                        return this;
                    }
                }
            }
        }
    }

    // stub
    class ValidatorUtils {
        class Validator {
            void validatePattern(String itemName, String value, String pattern) {
            }
        }
    }

    // stub
    class Pet {

        String getName() {
            return null;
        }

        long getId() {
            return 0L;
        }

        enum StatusEnum {
            AVAILABLE;

            String value() {
                return null;
            }
        }

        List<Tag> getTags() {
            return List.of();
        }
    }

    // stub
    class Tag {

        String getName() {
            return null;
        }
    }

    interface PetApi {
        ApiResponse<List<Pet>> findPetsByStatus(List<String> statusValues);
    }

    class PetApiImpl implements PetApi {

        static PetApi create(ApiClient apiClient) {
            return null;
        }

        @Override
        public ApiResponse<List<Pet>> findPetsByStatus(List<String> statusValues) {
            return null;
        }
    }

    class ApiClient {

        static Builder builder() {
            return null;
        }

        class Builder {

            ApiClient build() {
                return null;
            }

            Builder objectMapper(Object om) {
                return this;
            }

            Builder webClientBuilder(WebClientConfig.Builder webClientConfigBuilder) {
                return this;
            }

            WebClientConfig.Builder webClientBuilder() {
                return WebClientConfig.builder();
            }
        }
    }

    class ApiResponse<T> {

        HttpClientResponse webClientResponse() {
            return null;
        }

        T result() {
            return null;
        }
    }

    class Snippet1 {
        // tag::snippet_1[]
        public class PetServiceImpl extends PetService {
            @Override
            protected void handleAddPet(ServerRequest request, ServerResponse response,
                                        Pet pet) {
                response.status(Status.NOT_IMPLEMENTED_501).send();
            }
        }
        // end::snippet_1[]
    }

    class Snippet2 {
        // tag::snippet_2[]
        public class PetServiceImpl extends PetService {

            private final Map<Long, Pet> pets = new HashMap<>(); // <1>

            @Override
            protected void handleAddPet(ServerRequest request, ServerResponse response,
                                        Pet pet) {
                if (pets.containsKey(pet.getId())) { // <2>
                    AddPetOp.Response405.builder().send(response);
                }
                pets.put(pet.getId(), pet); // <3>
                AddPetOp.Response200.builder().send(response); // <4>
            }
        }
        // end::snippet_2[]
    }

    class Snippet3 {
        // tag::snippet_3[]
        public class PetServiceImpl extends PetService {

            private final Map<Long, Pet> pets = new HashMap<>(); // <1>

            @Override
            protected void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                                List<String> tags) { // <2>

                List<Pet> result = pets.values().stream()
                        .filter(pet -> pet.getTags()
                                .stream()
                                .anyMatch(petTag -> tags.contains(petTag.getName())))
                        .toList(); // <3>

                FindPetsByTagsOp.Response200.builder() // <4>
                        .response(result) // <5>
                        .send(response); // <6>

            }
        }
        // end::snippet_3[]
    }

    class Snippet4 {
        // tag::snippet_4[]
        public class AddPetOpCustom extends PetService.AddPetOp {
            @Override
            protected Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
                Pet result = request.content().hasEntity() // <1>
                        ? request.content().as(Pet.class)
                        : null;

                // Insist that pet names never start with a lower-case letter.
                if (result != null) {
                    validator.validatePattern("pet", result.getName(), "[^a-z].*"); // <2>
                }
                return result; // <3>
            }
        }
        // end::snippet_4[]
    }

    class Snippet5 {
        // tag::snippet_5[]
        public class PetServiceImpl extends PetService {
            @Override
            protected AddPetOp createAddPetOp() {
                return new AddPetOpCustom();
            }
        }
        // end::snippet_5[]
    }

    class Snippet6 {
        // tag::snippet_6[]
        public class ExampleClient {

            private ApiClient apiClient; // <1>

            void init() {
                ApiClient apiClient = ApiClient.builder().build(); // <2>
            }
        }
        // end::snippet_6[]
    }

    class Snippet7 {
        // tag::snippet_7[]
        public class ExampleClient {

            private ApiClient apiClient; // <1>

            void init() {
                ObjectMapper myObjectMapper = new ObjectMapper(); // <2>
                apiClient = ApiClient.builder()
                        .objectMapper(myObjectMapper) // <3>
                        .build();
            }
        }
        // end::snippet_7[]
    }

    class Snippet8 {
        // tag::snippet_8[]
        public class ExampleClient {

            private ApiClient apiClient; // <1>

            void init() {
                ApiClient.Builder apiClientAdjustedBuilder = ApiClient.builder(); // <2>

                apiClientAdjustedBuilder
                        .webClientBuilder() // <3>
                        .connectTimeout(Duration.ofSeconds(4)); // <4>

                apiClient = apiClientAdjustedBuilder.build(); // <5>
            }
        }
        // end::snippet_8[]
    }

    class Snippet9 {
        // tag::snippet_9[]
        public class ExampleClient {

            private ApiClient apiClient; // <1>

            void init() {
                WebClientConfig.Builder customWebClientBuilder = WebClient.builder() // <2>
                        .connectTimeout(Duration.ofSeconds(3)) // <3>
                        .baseUri("https://myservice.mycompany.com"); // <4>

                apiClient = ApiClient.builder() // <5>
                        .webClientBuilder(customWebClientBuilder) // <6>
                        .build(); // <7>
            }
        }
        // end::snippet_9[]
    }

    class Snippet10 {
        // tag::snippet_10[]
        public class ExampleClient {

            private ApiClient apiClient; // <1>

            private PetApi petApi; // <2>

            void preparePetApi() {
                petApi = PetApiImpl.create(apiClient); // <3>
            }
        }
        // end::snippet_10[]
    }

    class Snippet11 {
        PetApi petApi;

        // tag::snippet_11[]
        void findAvailablePets() {
            ApiResponse<List<Pet>> apiResponse =
                    petApi.findPetsByStatus(List.of(Pet.StatusEnum.AVAILABLE.value())); // <1>

            List<Pet> availablePets = apiResponse.result(); // <2>
        }
        // end::snippet_11[]
    }

    class Snippet12 {
        PetApi petApi;

        // tag::snippet_12[]
        void findAvailablePets() {
            ApiResponse<List<Pet>> apiResponse =
                    petApi.findPetsByStatus(List.of(Pet.StatusEnum.AVAILABLE.value())); // <1>

            try (HttpClientResponse webClientResponse = apiResponse.webClientResponse()) { // <2>
                if (webClientResponse.status().code() != 200) { // <3>
                    // Handle a non-successful status.
                }
            }

            List<Pet> avlPets = apiResponse.result(); // <4>
        }
        // end::snippet_12[]
    }
}



