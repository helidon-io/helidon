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
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import io.helidon.common.Api;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;

/**
 * Configuration of a single advertised alternative service.
 */
@Api.Incubating
public final class AltSvc {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final boolean enabled;
    private final String protocol;
    private final Optional<Integer> port;
    private final Optional<Duration> maxAge;
    private final boolean persist;

    private AltSvc(Builder builder) {
        this.enabled = builder.enabled;
        this.protocol = Objects.requireNonNull(builder.protocol, "protocol");
        this.port = Objects.requireNonNull(builder.port, "port");
        this.maxAge = Objects.requireNonNull(builder.maxAge, "maxAge");
        this.persist = builder.persist;
    }

    /**
     * Create a fluent API builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create from configuration.
     *
     * @param config config node
     * @return a new configured alternative service advertisement
     */
    public static AltSvc create(Config config) {
        return builder().config(Objects.requireNonNull(config, "config")).build();
    }

    /**
     * Create by customizing the fluent builder.
     *
     * @param consumer builder customizer
     * @return a new alternative service advertisement
     */
    public static AltSvc create(Consumer<Builder> consumer) {
        Builder builder = builder();
        Objects.requireNonNull(consumer, "consumer").accept(builder);
        return builder.build();
    }

    /**
     * Whether the alternative service should be advertised.
     *
     * @return whether advertisement is enabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Advertised protocol name.
     *
     * @return advertised protocol
     */
    public String protocol() {
        return protocol;
    }

    /**
     * Advertised port.
     *
     * @return advertised port
     */
    public Optional<Integer> port() {
        return port;
    }

    /**
     * Advertised max age.
     *
     * @return max age
     */
    public Optional<Duration> maxAge() {
        return maxAge;
    }

    /**
     * Whether to emit the {@code persist=1} parameter.
     *
     * @return whether to emit persist
     */
    public boolean persist() {
        return persist;
    }

    /**
     * Create the header for the provided listener port.
     *
     * @param listenerPort current listener port
     * @return header
     */
    public Header header(int listenerPort) {
        return HeaderValues.create(HeaderNames.ALT_SVC, headerValue(listenerPort));
    }

    /**
     * Create the header value for the provided listener port.
     *
     * @param listenerPort current listener port
     * @return header value
     */
    public String headerValue(int listenerPort) {
        int effectivePort = port.orElse(listenerPort);
        if (effectivePort < 1 || effectivePort > 65_535) {
            throw new IllegalArgumentException("Alt-Svc port must be between 1 and 65535.");
        }

        StringBuilder value = new StringBuilder();
        byte[] protocolBytes = protocol.getBytes(StandardCharsets.ISO_8859_1);
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
                value.append((char) candidate);
            } else {
                value.append('%')
                        .append(HEX[candidate >>> 4])
                        .append(HEX[candidate & 0x0F]);
            }
        }
        value.append("=\"")
                .append(':')
                .append(effectivePort)
                .append('"');

        if (maxAge.isPresent()) {
            value.append("; ma=").append(maxAge.get().toSeconds());
        }
        if (persist) {
            value.append("; persist=1");
        }
        return value.toString();
    }

    @Override
    public String toString() {
        return "AltSvc{"
                + "enabled=" + enabled
                + ", protocol='" + protocol + '\''
                + ", port=" + port.orElse(null)
                + ", maxAge=" + maxAge.orElse(null)
                + ", persist=" + persist
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, protocol, port, maxAge, persist);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AltSvc other)) {
            return false;
        }
        return enabled == other.enabled
                && persist == other.persist
                && Objects.equals(protocol, other.protocol)
                && Objects.equals(port, other.port)
                && Objects.equals(maxAge, other.maxAge);
    }

    /**
     * Fluent builder for {@link AltSvc}.
     */
    @Configured
    public static final class Builder {
        private boolean enabled = true;
        private String protocol = "h3";
        private Optional<Integer> port = Optional.empty();
        private Optional<Duration> maxAge = Optional.empty();
        private boolean persist;

        private Builder() {
        }

        /**
         * Update builder from config.
         *
         * @param config config node
         * @return updated builder
         */
        public Builder config(Config config) {
            Objects.requireNonNull(config, "config");
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("protocol").asString().ifPresent(this::protocol);
            config.get("port").asInt().ifPresent(this::port);
            config.get("max-age").as(Duration.class).ifPresent(this::maxAge);
            config.get("persist").asBoolean().ifPresent(this::persist);
            return this;
        }

        /**
         * Update builder by a consumer.
         *
         * @param consumer builder consumer
         * @return updated builder
         */
        public Builder update(Consumer<Builder> consumer) {
            Objects.requireNonNull(consumer, "consumer").accept(this);
            return this;
        }

        /**
         * Whether the alternative service should be advertised.
         *
         * @param enabled whether advertisement is enabled
         * @return updated builder
         */
        @ConfiguredOption(value = "true", experimental = true)
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Advertised protocol name.
         * Each Java character in the range {@code U+0000} through {@code U+00FF} maps one-to-one to an opaque protocol
         * identifier byte. When advertisement is enabled, the identifier must contain between {@code 1} and {@code 255}
         * bytes, inclusive. Whitespace-only identifiers are valid.
         * The value is validated by {@link #build()}.
         *
         * @param protocol advertised protocol
         * @return updated builder
         */
        @ConfiguredOption(value = "h3", experimental = true)
        public Builder protocol(String protocol) {
            this.protocol = Objects.requireNonNull(protocol, "protocol");
            return this;
        }

        /**
         * Advertised port.
         * The port must be between {@code 1} and {@code 65535}, inclusive, when advertisement is enabled.
         * The value is validated by {@link #build()}.
         *
         * @param port advertised port
         * @return updated builder
         */
        @ConfiguredOption(experimental = true)
        public Builder port(int port) {
            this.port = Optional.of(port);
            return this;
        }

        /**
         * Clear the advertised port so the listener port is used.
         *
         * @return updated builder
         */
        public Builder clearPort() {
            this.port = Optional.empty();
            return this;
        }

        /**
         * Advertised max age.
         * The maximum age must be non-negative when advertisement is enabled.
         * The value is validated by {@link #build()}.
         *
         * @param maxAge max age
         * @return updated builder
         */
        @ConfiguredOption(experimental = true)
        public Builder maxAge(Duration maxAge) {
            this.maxAge = Optional.of(Objects.requireNonNull(maxAge, "maxAge"));
            return this;
        }

        /**
         * Clear the advertised max age so the parameter is omitted.
         *
         * @return updated builder
         */
        public Builder clearMaxAge() {
            this.maxAge = Optional.empty();
            return this;
        }

        /**
         * Whether to emit the {@code persist=1} parameter.
         *
         * @param persist whether to emit persist
         * @return updated builder
         */
        @ConfiguredOption(value = "false", experimental = true)
        public Builder persist(boolean persist) {
            this.persist = persist;
            return this;
        }

        /**
         * Create a new {@link AltSvc} instance.
         * When advertisement is enabled, this method validates that the protocol contains between {@code 1} and
         * {@code 255} opaque bytes, that each Java character maps to one byte in the range {@code U+0000} through
         * {@code U+00FF}, that the advertised port is between {@code 1} and {@code 65535}, inclusive, and that the maximum
         * age is non-negative.
         *
         * @return a new alternative service advertisement
         * @throws IllegalArgumentException if an enabled advertisement contains an invalid protocol, port, or maximum age
         */
        public AltSvc build() {
            if (enabled) {
                if (protocol.isEmpty()) {
                    throw new IllegalArgumentException("Alt-Svc protocol cannot be empty when enabled.");
                }
                for (int index = 0; index < protocol.length(); index++) {
                    if (protocol.charAt(index) > 0xFF) {
                        throw new IllegalArgumentException(
                                "Alt-Svc protocol contains a character outside the byte range when enabled.");
                    }
                }
                if (protocol.length() > 0xFF) {
                    throw new IllegalArgumentException("Alt-Svc protocol exceeds the 255-byte ALPN limit when enabled.");
                }
                if (maxAge.isPresent() && maxAge.get().isNegative()) {
                    throw new IllegalArgumentException("Alt-Svc maxAge cannot be negative.");
                }
                if (port.isPresent() && (port.get() < 1 || port.get() > 65_535)) {
                    throw new IllegalArgumentException("Alt-Svc port must be between 1 and 65535 when enabled.");
                }
            }
            return new AltSvc(this);
        }
    }
}
