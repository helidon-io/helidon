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

package io.helidon.webserver.http;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;

/**
 * Runtime support for a single advertised alternative service.
 * Each instance is bound to at most one listener.
 */
@Api.Incubating
public final class AltSvc implements RuntimeType.Api<AltSvcConfig> {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final AltSvcConfig config;
    private final String headerPrefix;
    private final String headerSuffix;
    private final Optional<Header> explicitPortHeader;
    private final AtomicReference<ListenerHeader> listenerHeader = new AtomicReference<>();

    private AltSvc(AltSvcConfig config) {
        this.config = Objects.requireNonNull(config, "config");

        StringBuilder prefix = new StringBuilder();
        byte[] protocolBytes = config.protocol().getBytes(StandardCharsets.ISO_8859_1);
        for (byte current : protocolBytes) {
            int candidate = current & 0xFF;
            boolean tokenChar = (candidate >= '0' && candidate <= '9')
                    || (candidate >= 'A' && candidate <= 'Z')
                    || (candidate >= 'a' && candidate <= 'z')
                    || candidate == '!'
                    || candidate == '#'
                    || candidate == '$'
                    || candidate == '&'
                    || candidate == '\''
                    || candidate == '*'
                    || candidate == '+'
                    || candidate == '-'
                    || candidate == '.'
                    || candidate == '^'
                    || candidate == '_'
                    || candidate == '`'
                    || candidate == '|'
                    || candidate == '~';
            if (tokenChar) {
                prefix.append((char) candidate);
            } else {
                prefix.append('%')
                        .append(HEX[candidate >>> 4])
                        .append(HEX[candidate & 0x0F]);
            }
        }
        this.headerPrefix = prefix.append("=\"").append(':').toString();

        StringBuilder suffix = new StringBuilder().append('"');
        config.maxAge().ifPresent(maxAge -> suffix.append("; ma=").append(maxAge.toSeconds()));
        if (config.persist()) {
            suffix.append("; persist=1");
        }
        this.headerSuffix = suffix.toString();
        this.explicitPortHeader = config.port().map(this::createHeader);
    }

    /**
     * Create a fluent API builder.
     *
     * @return a new builder
     */
    public static AltSvcConfig.Builder builder() {
        return AltSvcConfig.builder();
    }

    /**
     * Create from configuration.
     *
     * @param config config node
     * @return a new alternative service runtime
     */
    public static AltSvc create(Config config) {
        return builder().config(Objects.requireNonNull(config, "config")).build();
    }

    /**
     * Create from its configuration prototype.
     *
     * @param config configuration prototype
     * @return a new alternative service runtime
     */
    public static AltSvc create(AltSvcConfig config) {
        return new AltSvc(config);
    }

    /**
     * Create by customizing the configuration builder.
     *
     * @param consumer builder customizer
     * @return a new alternative service runtime
     */
    public static AltSvc create(Consumer<AltSvcConfig.Builder> consumer) {
        return builder()
                .update(Objects.requireNonNull(consumer, "consumer"))
                .build();
    }

    @Override
    public AltSvcConfig prototype() {
        return config;
    }

    /**
     * Create the header for the provided listener port.
     *
     * @param listenerPort current listener port
     * @return header
     * @throws IllegalArgumentException if there is no valid effective port
     * @throws IllegalStateException if this instance is reused for a different listener port
     */
    public Header header(int listenerPort) {
        int effectivePort = config.port().orElse(listenerPort);
        if (!validPort(effectivePort)) {
            throw new IllegalArgumentException("Alt-Svc port must be between 1 and 65535.");
        }
        if (explicitPortHeader.isPresent()) {
            return explicitPortHeader.get();
        }

        ListenerHeader current = listenerHeader.get();
        if (current == null) {
            ListenerHeader created = new ListenerHeader(effectivePort, createHeader(effectivePort));
            if (listenerHeader.compareAndSet(null, created)) {
                return created.header();
            }
            current = listenerHeader.get();
        }
        if (current.port() != effectivePort) {
            throw new IllegalStateException("Alt-Svc instance is already bound to listener port "
                                                    + current.port() + ", cannot use listener port " + effectivePort + '.');
        }
        return current.header();
    }

    /**
     * Create the header value for the provided listener port.
     *
     * @param listenerPort current listener port
     * @return header value
     * @throws IllegalArgumentException if there is no valid effective port
     * @throws IllegalStateException if this instance is reused for a different listener port
     */
    public String headerValue(int listenerPort) {
        return header(listenerPort).get();
    }

    private static boolean validPort(int port) {
        return port > 0 && port <= 65_535;
    }

    private Header createHeader(int port) {
        return HeaderValues.createCached(HeaderNames.ALT_SVC, headerPrefix + port + headerSuffix);
    }

    private record ListenerHeader(int port, Header header) {
    }
}
