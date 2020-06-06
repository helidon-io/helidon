/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Configuration;

import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.client.spi.ConnectorProvider;

/**
 * Provider for Helidon WebClient {@link Connector} that utilizes the Helidon HTTP Client to send and receive
 * HTTP request and responses.
 * <p>
 * The following properties are only supported at construction of this class:
 * <ul>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#CONNECT_TIMEOUT}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#FOLLOW_REDIRECTS}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#PROXY_URI}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#PROXY_USERNAME}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#PROXY_PASSWORD}</li>
 * <li>{@link org.glassfish.jersey.client.ClientProperties#READ_TIMEOUT}</li>
 * <li>{@link HelidonProperties#CONFIG}</li>
 * </ul>
 * <p>
 * If a {@link org.glassfish.jersey.client.ClientResponse} is obtained and an
 * entity is not read from the response then
 * {@link org.glassfish.jersey.client.ClientResponse#close()} MUST be called
 * after processing the response to release connection-based resources.
 * </p>
 * <p>
 * Client operations are thread safe, the HTTP connection may
 * be shared between different threads.
 * </p>
 * <p>
 * If a response entity is obtained that is an instance of {@link java.io.Closeable}
 * then the instance MUST be closed after processing the entity to release
 * connection-based resources.
 * </p>
 * <p>
 * This connector uses {@link org.glassfish.jersey.client.ClientProperties#OUTBOUND_CONTENT_LENGTH_BUFFER} to buffer the entity
 * written for instance by {@link javax.ws.rs.core.StreamingOutput}. Should the buffer be small and
 * {@link javax.ws.rs.core.StreamingOutput#write(OutputStream)} be called many times, the performance can drop. The Content-Length
 * or the Content_Encoding header is set by the underlaying Helidon WebClient regardless of the
 * {@link org.glassfish.jersey.client.ClientProperties#OUTBOUND_CONTENT_LENGTH_BUFFER} size, however.
 * </p>
 *
 */
public class HelidonConnectorProvider implements ConnectorProvider {
    @Override
    public Connector getConnector(Client client, Configuration runtimeConfig) {
        return new HelidonConnector(client, runtimeConfig);
    }
}
