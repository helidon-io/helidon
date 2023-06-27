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

package io.helidon.microprofile.server;

import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.helidon.microprofile.cdi.HelidonContainer;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

/**
 * Server to handle lifecycle of microprofile implementation.
 */
public class ServerImpl implements Server {
    private static final System.Logger LOGGER = System.getLogger(Server.class.getName());

    private static final System.Logger STARTUP_LOGGER = System.getLogger("io.helidon.microprofile.startup.server");

    private final HelidonContainer helidonContainer = HelidonContainer.instance();
    private final SeContainer container;
    private final String host;
    private final ServerCdiExtension serverExtension;

    private int port = -1;

    ServerImpl(Builder builder) {
        this.container = (SeContainer) CDI.current();
        LOGGER.log(Level.TRACE, () -> "Container context id: " + HelidonContainer.instance().context().id());

        InetAddress listenHost;
        if (null == builder.host()) {
            listenHost = InetAddress.getLoopbackAddress();
        } else {
            try {
                listenHost = InetAddress.getByName(builder.host());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Failed to create address for host: " + builder.host(), e);
            }
        }
        this.host = listenHost.getHostName();

        BeanManager beanManager = container.getBeanManager();

        this.serverExtension = beanManager.getExtension(ServerCdiExtension.class);

        serverExtension.context(helidonContainer.context());

        serverExtension.serverBuilder()
                .port(builder.port())
                .address(listenHost);

        serverExtension.listenHost(this.host);

    }

    @Override
    public Server start() {
        STARTUP_LOGGER.log(Level.TRACE, ServerImpl.class.getName(), "start ENTRY");

        helidonContainer.start();

        STARTUP_LOGGER.log(Level.TRACE, "Started up");

        this.port = serverExtension.port();

        return this;
    }

    @Override
    public Server stop() {
        container.close();
        return this;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }
}
