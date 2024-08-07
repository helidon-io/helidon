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
    public static class Pet {

        public String getName() {
            return null;
        }

        public long getId() {
            return 0L;
        }

        public enum StatusEnum {
            AVAILABLE;

            public String value() {
                return "";
            }
        }

        public List<Tag> getTags() {
            return List.of();
        }


    }

    public static class Tag {

        public String getName() {
            return null;
        }
    }

    public interface PetApi {

        ApiResponse<List<Pet>> findPetsByStatus(List<String> statusValues);
    }

    public static class PetApiImpl implements PetApi {

        public static PetApi create(ApiClient apiClient) {
            return null;
        }

        @Override
        public ApiResponse<List<Pet>> findPetsByStatus(List<String> statusValues) {
            return null;
        }
    }

    public static class ApiClient {

        static Builder builder() {
            return null;
        }

        public static class Builder {

            public ApiClient build() {
                return null;
            }

            Builder objectMapper(Object om) {
                return this;
            }

            Builder webClientBuilder(WebClientConfig.Builder webClientConfigBuilder){
                return this;
            }

            WebClientConfig.Builder webClientBuilder() {
                return WebClientConfig.builder();
            }
        }
    }

    public static class ApiResponse<T> {

        public HttpClientResponse webClientResponse() {
            return null;
        }

        public T result() {
            return null;
        }
    }

    // tag::ExampleClientclassDeclFull[]
    // tag::ExampleClientclassDecl[]
    public class ExampleClient {

        private ApiClient apiClient;                                // <1>

        // end::ExampleClientclassDecl[]
        private PetApi petApi;                                      // <2>

        // end::ExampleClientclassDeclFull[]
        // tag::ExampleClientinitDecl[]
        void init() {
            // end::ExampleClientinitDecl[]
            // tag::ExampleClientDefaultApiClient[]
            ApiClient apiClient = ApiClient.builder().build();      // <2>
            // end::ExampleClientDefaultApiClient[]
            // tag::ExampleClientApiClientWithObjMapper[]
            ObjectMapper myObjectMapper = null;                     // <2>

            apiClient = ApiClient.builder()
                    .objectMapper(myObjectMapper)                   // <3>
                    .build();
            // end::ExampleClientApiClientWithObjMapper[]

            // tag::ExampleClientApiClientWithAdjustedBuilder[]
            ApiClient.Builder apiClientAdjustedBuilder = ApiClient.builder();   // <2>

            apiClientAdjustedBuilder
                    .webClientBuilder()                                         // <3>
                    .connectTimeout(Duration.ofSeconds(4));                     // <4>

            apiClient = apiClientAdjustedBuilder.build();                       // <5>
            // end::ExampleClientApiClientWithAdjustedBuilder[]
            // tag::ExampleClientApiClientWithCustomWebClientBuilder[]
            WebClientConfig.Builder customWebClientBuilder = WebClient.builder()// <2>
                    .connectTimeout(Duration.ofSeconds(3))                      // <3>
                    .baseUri("https://myservice.mycompany.com");                // <4>

            apiClient = ApiClient.builder()                                     // <5>
                    .webClientBuilder(customWebClientBuilder)                   // <6>
                    .build();                                                   // <7>
            // end::ExampleClientApiClientWithCustomWebClientBuilder[]
            // tag::ExampleClientinitEnd[]
        }
        // end::ExampleClientinitEnd[]

        // tag::ExampleClientPrepPetApi[]
        void preparePetApi() {
            petApi = PetApiImpl.create(apiClient);                  // <3>
        }
        // end::ExampleClientPrepPetApi[]
        // tag::ExampleClientFindAvailableDecl[]
        void findAvailablePets() {
            // end::ExampleClientFindAvailableDecl[]
            // tag::ExampleClientFindAvailableSimple[]
            ApiResponse<List<Pet>> apiResponse =
                    petApi.findPetsByStatus(List.of(Pet.StatusEnum.AVAILABLE.value())); // <1>
            // end::ExampleClientFindAvailableSimple[]
            // tag::ExampleClientFindAvailableResponseCheck[]

            try (HttpClientResponse webClientResponse = apiResponse.webClientResponse()) {  // <2>
                if (webClientResponse.status().code() != 200) {                             // <3>
                    // Handle a non-successful status.
                }
            }

            List<Pet> avlPets = apiResponse.result();                                       // <4>
            // end::ExampleClientFindAvailableResponseCheck[]
            // tag::ExampleClientFindAvailableUseResult[]

            List<Pet> availablePets = apiResponse.result();     // <2>
            // end::ExampleClientFindAvailableUseResult[]
            // tag::ExampleClientFindAvailableEnd[]
        }
        // end::ExampleClientFindAvailableEnd[]

        // tag::ExampleClientclassEnd[]
    }
    // end::ExampleClientclassEnd[]

    // tag::AddPetOpCustomClassDecl[]
    public class AddPetOpCustom extends PetService.AddPetOp {
        // end::AddPetOpCustomClassDecl[]

        // tag::AddPetOpCustompetMethod[]
        @Override
        protected Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
            Pet result = request.content().hasEntity()          // <1>
                    ? request.content().as(Pet.class)
                    : null;

            /*
             Insist that pet names never start with a lower-case letter.
             */
            if (result != null) {
                validator.validatePattern("pet", result.getName(), "[^a-z].*"); // <2>
            }
            return result;                                      // <3>
        }
        // end::AddPetOpCustompetMethod[]
    }

    public static class PetService {

        protected void handleAddPet(ServerRequest request, ServerResponse response, Pet pet) {
        }

        protected void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                            List<String> tags) {
        }

        protected AddPetOp createAddPetOp() {
            return null;
        }


        public static class AddPetOp {

            protected Pet pet(ServerRequest request, ValidatorUtils.Validator validator) {
                return null;
            }

            public record Response405() {

                public static Response405.Builder builder() {
                    return new Builder();
                }

                public static class Builder {
                    void send(ServerResponse response) {
                    }
                }
            }

            public record Response200(Pet response) {

                public static Response200.Builder builder() {
                    return new Builder();
                }

                public static class Builder {
                    void send(ServerResponse response) {

                    }
                }
            }
        }

        public static class FindPetsByTagsOp {

            public record Response200() {

                public static Response200.Builder builder() {
                    return new Response200.Builder();
                }

                public static class Builder {
                    public void send(ServerResponse response) {
                    }

                    public Builder response(List<Pet> result) {
                        return this;
                    }
                }
            }
        }
    }

    public static class ValidatorUtils {

        public static class Validator {
            void validatePattern(String itemName, String value, String pattern) {
            }
        }
    }

// tag::PetServiceImplCustomclass-declaration[]
// tag::PetServiceImplCustomclass-header[]
    public class PetServiceImpl extends PetService {
// end::PetServiceImplCustomclass-header[]
// tag::PetServiceImplCustomaddedFields[]

        private final Map<Long, Pet> pets = new HashMap<>(); // <1>

// end::PetServiceImplCustomaddedFields[]
// tag::PetServiceImplCustomhandleAddPetDecl[]
        @Override
        protected void handleAddPet(ServerRequest request, ServerResponse response,
                                    Pet pet) {
// end::PetServiceImplCustomhandleAddPetDecl[]
// tag::PetServiceImplCustomhandleAddPetGenerated[]
            response.status(Status.NOT_IMPLEMENTED_501).send();
// end::PetServiceImplCustomhandleAddPetGenerated[]
// tag::PetServiceImplCustomhandleAddPetCustom[]
            if (pets.containsKey(pet.getId())) {            // <2>
                AddPetOp.Response405.builder().send(response);
            }
            pets.put(pet.getId(), pet);                     // <3>
            AddPetOp.Response200.builder().send(response);  // <4>
// end::PetServiceImplCustomhandleAddPetCustom[]
// tag::PetServiceImplCustomhandleAddPetEnd[]
        }
// end::PetServiceImplCustomhandleAddPetEnd[]

// tag::PetServiceImplCustomhandleFindPetsByTagsDecl[]
        @Override
        protected void handleFindPetsByTags(ServerRequest request, ServerResponse response,
                                            List<String> tags) { // <2>
// end::PetServiceImplCustomhandleFindPetsByTagsDecl[]
// tag::PetServiceImplCustomhandleFindPetsByTagsCustom[]

            List<Pet> result = pets.values().stream()
                    .filter(pet -> pet.getTags()
                            .stream()
                            .anyMatch(petTag -> tags.contains(petTag.getName())))
                    .toList();                                  // <3>

            FindPetsByTagsOp.Response200.builder()              // <4>
                    .response(result)                           // <5>
                    .send(response);                            // <6>

// end::PetServiceImplCustomhandleFindPetsByTagsCustom[]
            response.status(Status.NOT_IMPLEMENTED_501).send();
// tag::PetServiceImplCustomhandleFindPetsByTagsEnd[]
        }
// end::PetServiceImplCustomhandleFindPetsByTagsEnd[]

// tag::PetServiceImplCustomcreateAddPetOp[]
        @Override
        protected AddPetOp createAddPetOp() {
            return new AddPetOpCustom();
        }
// end::PetServiceImplCustomcreateAddPetOp[]
// tag::PetServiceImplCustomclass-end[]
    }
    // end::PetServiceImplCustomclass-end[]
// end::PetServiceImplCustomclass-declaration[]
}


