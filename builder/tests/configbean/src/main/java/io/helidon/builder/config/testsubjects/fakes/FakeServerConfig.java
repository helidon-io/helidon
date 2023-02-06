/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.config.testsubjects.fakes;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.Singular;
import io.helidon.builder.config.ConfigBean;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * aka ServerConfiguration.
 */
@ConfigBean
public interface FakeServerConfig extends FakeSocketConfig {

    /**
     * Returns the count of threads in the pool used to process HTTP requests.
     * <p>
     * Default value is {@link Runtime#availableProcessors()}.
     *
     * @return a workers count
     */
    int workersCount();

    /**
     * Returns a server port to listen on with the default server socket. If port is
     * {@code 0} then any available ephemeral port will be used.
     *
     * @return the server port of the default server socket
     */
    @Override
    int port();

    /**
     * Returns a maximum length of the queue of incoming connections on the default server
     * socket.
     * <p>
     * Default value is {@link FakeSocketConfig#DEFAULT_BACKLOG_SIZE}.
     *
     * @return a maximum length of the queue of incoming connections
     */
    @Override
    int backlog();

    /**
     * Returns a default server socket timeout in milliseconds or {@code 0} for an infinite timeout.
     *
     * @return a default server socket timeout in milliseconds or {@code 0}
     */
    @Override
    int timeoutMillis();

    /**
     * Returns proposed value of the TCP receive window that is advertised to the remote peer on the
     * default server socket.
     * <p>
     * If {@code 0} then use implementation default.
     *
     * @return a buffer size in bytes of the default server socket or {@code 0}
     */
    @Override
    int receiveBufferSize();

    /**
     * A socket configuration of an additional named server socket.
     * <p>
     * An additional named server socket may have a dedicated {@link FakeRoutingConfig} configured
     *
     * @param name the name of the additional server socket
     * @return an additional named server socket configuration or {@code empty} if there is no such
     * named server socket configured
     */
    default Optional<FakeSocketConfig> namedSocket(String name) {
        return Optional.ofNullable(sockets().get(name));
    }

    /**
     * A map of all the configured server sockets; that is the default server socket.
     *
     * @return a map of all the configured server sockets, never null
     */
    @Singular("socket")
    Map<String, FakeSocketConfig> sockets();

    /**
     * The maximum amount of time that the server will wait to shut
     * down regardless of the value of any additionally requested
     * quiet period.
     *
     * <p>The default implementation of this method returns {@link
     * java.time.Duration#ofSeconds(long) Duration.ofSeconds(10L)}.</p>
     *
     * @return the {@link java.time.Duration} to use
     */
    @ConfiguredOption(key = "whatever")
    default Duration maxShutdownTimeout() {
        return Duration.ofSeconds(10L);
    }

    /**
     * The quiet period during which the webserver will wait for new
     * incoming connections after it has been told to shut down.
     *
     * <p>The webserver will wait no longer than the duration returned
     * by the {@link #maxShutdownTimeout()} method.</p>
     *
     * <p>The default implementation of this method returns {@link
     * java.time.Duration#ofSeconds(long) Duration.ofSeconds(0L)}, indicating
     * that there will be no quiet period.</p>
     *
     * @return the {@link java.time.Duration} to use
     */
    default Duration shutdownQuietPeriod() {
        return Duration.ofSeconds(0L);
    }

    /**
     * Whether to print details of HelidonFeatures.
     *
     * @return whether to print details
     */
    boolean printFeatureDetails();

}
