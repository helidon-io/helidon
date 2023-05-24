/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.arquillian;

import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.container.test.spi.ContainerMethodExecutor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.container.test.spi.command.CommandCallback;

/**
 * Class HelidonLocalProtocol.
 */
public class HelidonLocalProtocol implements Protocol<HelidonProtocolConfiguration> {

    static final String PROTOCOL_NAME = "HelidonLocal";

    @Override
    public Class<HelidonProtocolConfiguration> getProtocolConfigurationClass() {
        return HelidonProtocolConfiguration.class;
    }

    @Override
    public ProtocolDescription getDescription() {
        return new ProtocolDescription(PROTOCOL_NAME);
    }

    @Override
    public DeploymentPackager getPackager() {
        return new HelidonLocalPackager();
    }

    @Override
    public ContainerMethodExecutor getExecutor(HelidonProtocolConfiguration protocolConfiguration,
                                               ProtocolMetaData metaData, CommandCallback callback) {
        return new HelidonMethodExecutor();
    }
}
