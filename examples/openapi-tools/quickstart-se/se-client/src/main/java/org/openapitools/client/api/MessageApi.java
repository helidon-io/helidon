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

import org.openapitools.client.ApiResponse;
import org.openapitools.client.model.Message;

/**
 * OpenAPI Helidon Quickstart
 *
 * <p>This is a sample for Helidon Quickstart project.
 */
public interface MessageApi {

    /**
     * Return a worldly greeting message.
     *
     * @return {@code ApiResponse<Message>}
     */
    ApiResponse<Message> getDefaultMessage();

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param name the name to greet (required)
     * @return {@code ApiResponse<Message>}
     */
    ApiResponse<Message> getMessage(String name);

    /**
     * Set the greeting to use in future messages.
     *
     * @param message Message for the user (required)
     * @return {@code ApiResponse<Void>}
     */
    ApiResponse<Void> updateGreeting(Message message);

}
