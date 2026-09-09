/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.messaging.spi.MessagingConnector;
import io.helidon.messaging.spi.MessagingConnectorProvider;

/**
 * Messaging configuration.
 */
@Prototype.Blueprint(decorator = MessagingConfigSupport.BuilderDecorator.class)
@Prototype.Sealed
@Prototype.CustomMethods(MessagingConfigSupport.GraphCustomMethods.class)
@Prototype.Configured("messaging")
@Prototype.RegistrySupport
@Api.Preview
interface MessagingConfigBlueprint extends Prototype.Factory<MessagingGraph> {
    /**
     * Maximum number of admitted deliveries waiting for execution. Zero disables buffering.
     *
     * @return queue capacity
     */
    @Option.Configured
    @Option.DefaultInt(0)
    int queueCapacity();

    /**
     * Positive maximum number of waiting callers and open connector reservations.
     *
     * @return maximum pending admissions
     */
    @Option.Configured
    @Option.DefaultInt(64)
    int maxPendingAdmissions();

    /**
     * Positive maximum number of messages retained by waiting callers and open connector reservations.
     *
     * @return maximum pending messages
     */
    @Option.Configured
    @Option.DefaultInt(1024)
    int maxPendingMessages();

    /**
     * Positive maximum number of admitted messages, including queued and executing deliveries.
     *
     * @return maximum in-flight messages
     */
    @Option.Configured
    @Option.DefaultInt(1024)
    int maxInFlightMessages();

    /**
     * Positive maximum capacity-wait time, representable in nanoseconds. Absence means no timeout.
     *
     * @return admission timeout
     */
    @Option.Configured
    Optional<Duration> admissionTimeout();

    /**
     * Positive global maximum shutdown and failed-startup rollback time, representable in nanoseconds.
     * This does not bound startup or readiness and cannot be overridden by a channel.
     *
     * @return shutdown timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration shutdownTimeout();

    /**
     * Logical channel settings, keyed by channel name.
     *
     * @return channel settings
     */
    @Option.Configured
    @Option.Singular
    Map<String, MessagingChannelConfig> channel();

    /**
     * Consumer and processor registrations contributing channel outputs.
     *
     * @return registrations
     */
    @Option.Singular
    List<ConsumerRegistration> consumerRegistrations();

    /**
     * Emitter registrations contributing typed producer metadata.
     *
     * @return registrations
     */
    @Option.Singular
    List<EmitterRegistration> emitterRegistrations();

    /**
     * Programmatic channel handles.
     *
     * @return channel handles
     */
    @Option.Access("")
    @Option.Singular
    List<MessagingChannel<?>> channelHandles();

    /**
     * Programmatic incoming channel connections.
     *
     * @return incoming channel connections
     */
    @Option.Access("")
    @Option.Singular
    Map<MessagingChannel<?>, IncomingChannel> incomingConnections();

    /**
     * Configured messaging connectors.
     *
     * @return configured connectors
     */
    @Option.Configured
    @Option.Provider(value = MessagingConnectorProvider.class,
                     discoverServices = false,
                     configForm = Option.Provider.ConfigForm.OBJECT_OR_LIST)
    @Option.Singular
    List<MessagingConnector> connector();

    /**
     * Incoming channel configurations, keyed by channel name.
     *
     * @return incoming channel configurations
     */
    @Option.Configured
    Map<String, Config> incoming();

    /**
     * Outgoing channel configurations, keyed by channel name.
     *
     * @return outgoing channel configurations
     */
    @Option.Configured
    Map<String, Config> outgoing();
}
