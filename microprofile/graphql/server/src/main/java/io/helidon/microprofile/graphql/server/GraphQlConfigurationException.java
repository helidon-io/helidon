/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

/**
 * Defines an exception that is critical enough that
 * will cause the GraphQL application to not start.
 */
public class GraphQlConfigurationException extends RuntimeException {
    /**
     * Construct a {@link GraphQlConfigurationException}.
     */
    public GraphQlConfigurationException() {
        super();
    }

    /**
     * Construct a {@link GraphQlConfigurationException} with a given message.
     * @param message exception message
     */
    public GraphQlConfigurationException(String message) {
        super(message);
    }

    /**
     * Construct a {@link GraphQlConfigurationException} with a given message and {@link Throwable}.
     * @param message exception message
     * @param throwable {@link Throwable}
     */
    public GraphQlConfigurationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
