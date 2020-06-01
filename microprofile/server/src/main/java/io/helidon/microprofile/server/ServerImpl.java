/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import io.helidon.microprofile.cdi.HelidonContainer;

/**
 * Server to handle lifecycle of microprofile implementation.
 */
public class ServerImpl implements Server {
    private static final Logger STARTUP_LOGGER = Logger.getLogger("io.helidon.microprofile.startup.server");

    private final HelidonContainer helidonContainer = HelidonContainer.instance();
    private final SeContainer container;
    private final String host;
    private final ServerCdiExtension serverExtension;

    private int port = -1;

    ServerImpl(Builder builder) {
        this.container = (SeContainer) CDI.current();

        InetAddress listenHost;
        if (null == builder.host()) {
            listenHost = InetAddress.getLoopbackAddress();
        } else {
            try {
                listenHost = InetAddress.getByName(builder.host());
            } catch (UnknownHostException e) {
                throw new MpException("Failed to create address for host: " + builder.host(), e);
            }
        }
        this.host = listenHost.getHostName();

        BeanManager beanManager = container.getBeanManager();

        this.serverExtension = beanManager.getExtension(ServerCdiExtension.class);

        serverExtension.serverBuilder()
                .context(helidonContainer.context())
                .port(builder.port())
                .bindAddress(listenHost);

        serverExtension.listenHost(this.host);

        STARTUP_LOGGER.finest("Builders ready");

        STARTUP_LOGGER.finest("Static classpath");

        STARTUP_LOGGER.finest("Static path");

        STARTUP_LOGGER.finest("Registered jersey application(s)");

        STARTUP_LOGGER.finest("Registered WebServer services");

        STARTUP_LOGGER.finest("Server created");
    }

    @Override
    public Server start() {
        STARTUP_LOGGER.entering(ServerImpl.class.getName(), "start");

        helidonContainer.start();

        STARTUP_LOGGER.finest("Started up");

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
