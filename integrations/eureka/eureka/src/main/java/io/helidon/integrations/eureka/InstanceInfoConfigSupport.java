/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.integrations.eureka;

import java.lang.System.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Services;

import static java.lang.System.Logger.Level.WARNING;
import static java.lang.System.getLogger;

/**
 * Support for additional customization of the {@link InstanceInfoConfig} prototype.
 */
final class InstanceInfoConfigSupport {

    private InstanceInfoConfigSupport() {
        super();
    }

    static final class BuilderDecorator implements Prototype.BuilderDecorator<InstanceInfoConfig.BuilderBase<?, ?>> {

        private static final Logger LOGGER = getLogger(BuilderDecorator.class.getName());

        BuilderDecorator() {
            super();
        }

        @Override // Prototype.BuilderDecorator<InstanceInfoConfig.BuilderBase<?, ?>>
        public void decorate(InstanceInfoConfig.BuilderBase<?, ?> builder) {
            if (builder.hostName().isEmpty()) {
                // Get or compute and set a default hostName value.if we can.
                builder.hostName(builder.config()
                                 .orElseGet(Config::empty)
                                 .get("hostName")
                                 .asString()
                                 .orElseGet(() -> localhost().map(InetAddress::getHostName)
                                            .orElse(""))); // Empty string (!) is native Eureka's fallback
            }
            if (builder.ipAddr().isEmpty()) {
                // Get or compute and set a default ipAddr value.if we can.
                builder.ipAddr(builder.config()
                               .orElseGet(Config::empty)
                               .get("ipAddr")
                               .asString()
                               .orElseGet(() -> localhost().map(InetAddress::getHostAddress)
                                          .orElse(""))); // Empty string (!) is native Eureka's fallback
            }
            if (builder.healthCheckUrlPath().isEmpty()) {
                // Get or compute and set a default healthCheckUrlPath value if we can.
                builder.healthCheckUrlPath(builder.config()
                                           .orElseGet(() -> Services.get(Config.class))
                                           .root()
                                           .get("server.reatures.observe.observers.health.endpoint")
                                           .asString()
                                           .orElse("/observe/health")); // Helidon convention, not native Eureka
            }
            PortInfoConfig portInfo = builder.portInfo();
            if (portInfo.port() == 0) {
                // Compute and set a default portInfo value.
                builder.portInfo(PortInfoConfig.builder()
                                 .from(portInfo)
                                 .enabled(true)
                                 .port(80)
                                 .build());
            }
            portInfo = builder.securePortInfo();
            if (portInfo.port() == 0) {
                // Compute and set a default securePortInfo value.
                builder.securePortInfo(PortInfoConfig.builder()
                                       .from(portInfo)
                                       .enabled(false)
                                       .port(443)
                                       .build());
            }

        }

        private static Optional<InetAddress> localhost() {
            // https://github.com/Netflix/eureka/blob/v2.0.4/eureka-client/src/main/java/com/netflix/appinfo/AbstractInstanceConfig.java#L216-L226
            try {
                return Optional.of(InetAddress.getLocalHost());
            } catch (UnknownHostException e) {
                if (LOGGER.isLoggable(WARNING)) {
                    LOGGER.log(WARNING, e);
                }
                return Optional.empty();
            }
        }

    }

    static final class CustomMethods {

        private CustomMethods() {
            super();
        }

        /**
         * Returns the prototype's {@linkplain InstanceInfoConfig#instanceId() instance ID} or a default value.
         *
         * @param prototype the prototype
         *
         * @param actualPort the actual port; used when computing default value
         *
         * @return the instance ID
         */
        @Prototype.PrototypeMethod
        static String instanceId(InstanceInfoConfig prototype, int actualPort) {
            return prototype
                .instanceId()
                .orElseGet(() -> prototype.hostName() + ":" + actualPort);
        }

        /**
         * Returns the prototype's effective {@linkplain InstanceInfoConfig#port() port}, taking into account what the
         * actual port is and whether TLS is in effect or not.
         *
         * @param prototype the prototype
         *
         * @param actualPort the actual port
         *
         * @param tls whether or not {@code tls} is in effect
         *
         * @return the appropriate port
         */
        @Prototype.PrototypeMethod
        static int port(InstanceInfoConfig prototype, int actualPort, boolean tls) {
            return tls ? prototype.portInfo().port() : actualPort;
        }

        /**
         * Returns whether the effective port is enabled.
         *
         * @param prototype the prototype
         *
         * @param tls whether or not TLS is in effect
         *
         * @return whether the effective port is enabled
         */
        @Prototype.PrototypeMethod
        static boolean portEnabled(InstanceInfoConfig prototype, boolean tls) {
            return tls ? prototype.portInfo().enabled() : true;
        }

        /**
         * Returns the prototype's effective {@linkplain InstanceInfoConfig#securePort() secure port}, taking into
         * account what the actual port is and whether TLS is in effect or not.
         *
         * @param prototype the prototype
         *
         * @param actualPort the actual port
         *
         * @param tls whether or not {@code tls} is in effect
         *
         * @return the appropriate secure port
         */
        @Prototype.PrototypeMethod
        static int securePort(InstanceInfoConfig prototype, int actualPort, boolean tls) {
            return tls ? actualPort : prototype.securePortInfo().port();
        }

        /**
         * Returns whether the effective secure port is enabled.
         *
         * @param prototype the prototype
         *
         * @param tls whether or not TLS is in effect
         *
         * @return whether the effective secure port is enabled
         */
        @Prototype.PrototypeMethod
        static boolean securePortEnabled(InstanceInfoConfig prototype, boolean tls) {
            return tls ? true : prototype.securePortInfo().enabled();
        }

    }

}
