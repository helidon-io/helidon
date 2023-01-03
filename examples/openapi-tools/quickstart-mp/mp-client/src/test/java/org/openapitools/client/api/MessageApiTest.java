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

import org.openapitools.client.model.Message;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI Helidon Quickstart Test
 *
 * API tests for MessageApi
 */
public class MessageApiTest {

    private static MessageApi client;
    private static final String baseUrl = "http://localhost:8080";

    @BeforeAll
    public static void setup() throws MalformedURLException {
        client = RestClientBuilder.newBuilder()
                        .baseUrl(new URL(baseUrl))
                        .register(ApiException.class)
                        .build(MessageApi.class);
    }

    
    /**
     * Return a worldly greeting message.
     *
     * @throws ApiException
     *          if the Api call fails
     */
    @Test
    public void getDefaultMessageTest() throws Exception {
        //Message response = client.getDefaultMessage();
        //assertNotNull(response);
    }
    
    /**
     * Return a greeting message using the name that was provided.
     *
     * @throws ApiException
     *          if the Api call fails
     */
    @Test
    public void getMessageTest() throws Exception {
        //Message response = client.getMessage(name);
        //assertNotNull(response);
    }
    
    /**
     * Set the greeting to use in future messages.
     *
     * @throws ApiException
     *          if the Api call fails
     */
    @Test
    public void updateGreetingTest() throws Exception {
        //void response = client.updateGreeting(message);
        //assertNotNull(response);
    }
    
}
