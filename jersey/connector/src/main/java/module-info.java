/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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


/**
 * A {@link org.glassfish.jersey.client.spi.Connector} that utilizes the Helidon HTTP Client to send
 * and receive HTTP request and responses.
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.jersey.connector {

    requires io.helidon.config;
    requires io.helidon.webclient;
    requires io.helidon.webclient.http2;
    requires jakarta.ws.rs;

    requires jersey.common;

    requires transitive jersey.client;

    exports io.helidon.jersey.connector;

    provides org.glassfish.jersey.client.spi.ConnectorProvider
            with io.helidon.jersey.connector.HelidonConnectorProvider;

}
