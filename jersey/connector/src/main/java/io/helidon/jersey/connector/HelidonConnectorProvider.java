/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Configuration;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.client.spi.ConnectorProvider;

/**
 * A Jersey {@link ConnectorProvider} that uses a {@link io.helidon.webclient.api.WebClient}
 * instance to executed HTTP requests on behalf of a Jakarta REST {@link Client}.
 * <p>
 * An instance of this class can be specified during the creation of a {@link Client}
 * using the method {@link org.glassfish.jersey.client.ClientConfig#connectorProvider}.
 * It is recommended to use the static method {@link #create()} for obtain an
 * instance of this class.
 * <p>
 * Configuration of a connector is driven by properties set on a {@link Client}
 * instance, including possibly a config tree. There is a combination of Jersey
 * and Helidon properties that can be specified for that purpose. Jersey properties
 * are defined in class {@link org.glassfish.jersey.client.ClientProperties} and Helidon
 * properties are defined in {@link HelidonProperties}.
 * <p>
 * Only the following properties from {@link org.glassfish.jersey.client.ClientProperties}
 * are supported:
 * <ul>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#CONNECT_TIMEOUT}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#FOLLOW_REDIRECTS}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#READ_TIMEOUT}</li>
 * </ul>
 * <p>
 * If a {@link org.glassfish.jersey.client.ClientResponse} is obtained and an
 * entity is not read from the response then
 * {@link org.glassfish.jersey.client.ClientResponse#close()} MUST be called
 * after processing the response to release connection-based resources.
 * <p>
 * Client operations are thread safe, the HTTP connection may be shared between
 * different threads.
 * <p>
 * If a response entity is obtained that is an instance of {@link java.io.Closeable}
 * then the instance MUST be closed after processing the entity to release
 * connection-based resources.
 */
public class HelidonConnectorProvider implements ConnectorProvider {
    /**
     * Default constructor is required for extensibility of Jersey.
     */
    public HelidonConnectorProvider() {
    }

    @Override
    public Connector getConnector(Client client, Configuration runtimeConfig) {
        return new HelidonConnector(client, runtimeConfig);
    }

    /**
     * Create a new instance of {@link HelidonConnectorProvider}.
     *
     * @return new instance of this class
     */
    public static HelidonConnectorProvider create() {
        return new HelidonConnectorProvider();
    }
}
