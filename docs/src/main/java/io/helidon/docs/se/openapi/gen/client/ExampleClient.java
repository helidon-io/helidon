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
package io.helidon.docs.se.openapi.gen.client;

import java.time.Duration;
import java.util.List;

import io.helidon.docs.se.openapi.gen.client.api.PetApi;
import io.helidon.docs.se.openapi.gen.client.api.PetApiImpl;
import io.helidon.docs.se.openapi.gen.client.model.Pet;
import io.helidon.webclient.api.HttpClientResponse;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.api.WebClientConfig;

import com.fasterxml.jackson.databind.ObjectMapper;

// tag::classDeclFull[]
// tag::classDecl[]
public class ExampleClient {

    private ApiClient apiClient;                                // <1>

// end::classDecl[]
    private PetApi petApi;                                      // <2>

// end::classDeclFull[]
// tag::initDecl[]
    void init() {
// end::initDecl[]
// tag::defaultApiClient[]
        ApiClient apiClient = ApiClient.builder().build();      // <2>
// end::defaultApiClient[]
// tag::apiClientWithObjMapper[]
        ObjectMapper myObjectMapper = null;                     // <2>

        apiClient = ApiClient.builder()
                .objectMapper(myObjectMapper)                   // <3>
                .build();
// end::apiClientWithObjMapper[]

// tag::apiClientWithAdjustedBuilder[]
        ApiClient.Builder apiClientAdjustedBuilder = ApiClient.builder();   // <2>

        apiClientAdjustedBuilder
                .webClientBuilder()                                         // <3>
                .connectTimeout(Duration.ofSeconds(4));                     // <4>

        apiClient = apiClientAdjustedBuilder.build();                       // <5>
// end::apiClientWithAdjustedBuilder[]
// tag::apiClientWithCustomWebClientBuilder[]
        WebClientConfig.Builder customWebClientBuilder = WebClient.builder()// <2>
                .connectTimeout(Duration.ofSeconds(3))                      // <3>
                .baseUri("https://myservice.mycompany.com");                // <4>

        apiClient = ApiClient.builder()                                     // <5>
                .webClientBuilder(customWebClientBuilder)                   // <6>
                .build();                                                   // <7>
// end::apiClientWithCustomWebClientBuilder[]
// tag::initEnd[]
    }
// end::initEnd[]

// tag::prepPetApi[]
    void preparePetApi() {
        petApi = PetApiImpl.create(apiClient);                  // <3>
    }
// end::prepPetApi[]
// tag::findAvailableDecl[]
    void findAvailablePets() {
// end::findAvailableDecl[]
// tag::findAvailableSimple[]
        ApiResponse<List<Pet>> apiResponse =
                petApi.findPetsByStatus(List.of(Pet.StatusEnum.AVAILABLE.value())); // <1>
// end::findAvailableSimple[]
// tag::findAvailableResponseCheck[]

        try (HttpClientResponse webClientResponse = apiResponse.webClientResponse()) {  // <2>
            if (webClientResponse.status().code() != 200) {                             // <3>
                // Handle a non-successful status.
            }
        }

        List<Pet> avlPets = apiResponse.result();                                       // <4>
// end::findAvailableResponseCheck[]
// tag::findAvailableUseResult[]

        List<Pet> availablePets = apiResponse.result();     // <2>
// end::findAvailableUseResult[]
// tag::findAvailableEnd[]
    }
// end::findAvailableEnd[]

// tag::classEnd[]
}
// end::classEnd[]
