/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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


package org.openapitools.client.api;

import java.util.List;
import java.util.Map;
import org.openapitools.client.model.Message;

import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiResponse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.common.reactive.Single;
import io.helidon.webclient.WebClientResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * 
 * OpenAPI Helidon Quickstart Test
 *
 * 
 * API tests for MessageApi
 */
public class MessageApiTest {

    private static ApiClient apiClient;
    private static MessageApi api;
    private static final String baseUrl = "http://localhost:8080";

    @BeforeAll
    public static void setup() {
        apiClient = ApiClient.builder().build();
        api = MessageApiImpl.create(apiClient);
    }

   /**
    * Return a worldly greeting message.
    */
    @Test
    public void getDefaultMessageTest() {

        // TODO - uncomment the following two lines to invoke the service with valid parameters.
        //ApiResponse<Message> response = api.getDefaultMessage();
        //response.webClientResponse().await();
        // TODO - check for appropriate return status
        // assertThat("Return status", response.get().status().code(), is(expectedStatus));

        // TODO: test validations
    }

   /**
    * Return a greeting message using the name that was provided.
    */
    @Test
    public void getMessageTest() {
        // TODO - assign values to the input arguments.
        String name = null;

        // TODO - uncomment the following two lines to invoke the service with valid parameters.
        //ApiResponse<Message> response = api.getMessage(name);
        //response.webClientResponse().await();
        // TODO - check for appropriate return status
        // assertThat("Return status", response.get().status().code(), is(expectedStatus));

        // TODO: test validations
    }

   /**
    * Set the greeting to use in future messages.
    */
    @Test
    public void updateGreetingTest() {
        // TODO - assign values to the input arguments.
        Message message = null;

        // TODO - uncomment the following two lines to invoke the service with valid parameters.
        //ApiResponse<Void> response = api.updateGreeting(message);
        //response.webClientResponse().await();
        // TODO - check for appropriate return status
        // assertThat("Return status", response.get().status().code(), is(expectedStatus));

        // TODO: test validations
    }

}
