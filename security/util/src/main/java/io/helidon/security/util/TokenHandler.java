/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import static java.util.Collections.singletonList;

/**
 * Extracts a security token from request or updates headers with the token.
 * Currently supports headers as sources of tokens. The token is then extracted either
 * with a prefix (e.g. basic ) or with a regular expression (first group in the regexp).
 * When building header, it is created in the same way. To create a more complicated header, you can
 * use configuration option token-format that will be processed using {@link String#format(String, Object...)} with the token
 * as a single string argument.
 */
public final class TokenHandler {
    private final String tokenHeader;
    private final Function<String, String> headerExtractor;
    private final Function<String, String> headerCreator;

    private TokenHandler(Builder builder) {
        this.tokenHeader = builder.tokenHeader;
        if (null != builder.tokenPattern) {
            Pattern tokenPattern = builder.tokenPattern;
            this.headerExtractor = s -> {
                Matcher m = tokenPattern.matcher(s);
                if (m.matches()) {
                    return m.group(1);
                }
                throw new SecurityException("Header does not match expected pattern: " + s);
            };
        } else if (null != builder.tokenPrefix) {
            int len = builder.tokenPrefix.length();
            String lcPrefix = builder.tokenPrefix.toLowerCase();

            this.headerExtractor = s -> {
                if (s.toLowerCase().startsWith(lcPrefix)) {
                    return s.substring(len);
                }
                throw new SecurityException("Header does not start with expected prefix " + lcPrefix + ", it is: " + s);
            };
        } else {
            this.headerExtractor = s -> s;
        }

        if (null != builder.tokenFormat) {
            String format = builder.tokenFormat;

            this.headerCreator = s -> String.format(format, s);
        } else if (null != builder.tokenPrefix) {
            String prefix = builder.tokenPrefix;

            this.headerCreator = s -> prefix + s;
        } else {
            this.headerCreator = s -> s;
        }
    }

    /**
     * Fluent API builder to create {@link TokenHandler}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A shortcut method to build a token handler that does not modify the token content.
     *
     * @param header header name (to read or write to)
     * @return a new instance for the header name
     */
    public static TokenHandler forHeader(String header) {
        return builder().tokenHeader(header).build();
    }

    /**
     * Create a {@link TokenHandler} from configuration.
     * Expected configuration (to be located on token key):
     * <pre>
     * token {
     *   header = "Authorization"
     *   # or do not specify - then the whole header is considered to be the token value
     *   prefix = "bearer "
     *   # optional alternative - looking for first matching group
     *   #regexp = "bearer (.*)"
     * }
     * </pre>
     *
     * @param config config to parse into an instance of this object
     * @return a new instance configured from config
     */
    public static TokenHandler create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Extracts the token from request.
     * If expected header is not present, returns empty optional, otherwise
     * parses the token according to configuration.
     * If the header does not satisfy the configuration (e.g. invalid prefix, wrong pattern), throws exception.
     *
     * @param headers Headers to extract token from
     * @return token value or empty in case the token is not present in request
     * @throws SecurityException in case the token data is malformed
     */
    public Optional<String> extractToken(Map<String, List<String>> headers) {
        List<String> tokenHeaders = headers.getOrDefault(tokenHeader, Collections.emptyList());
        if (tokenHeaders.isEmpty()) {
            // I only understand configured header, ignore everything else
            return Optional.empty();
        }
        if (tokenHeaders.size() > 1) {
            // we can try to find the one that matches (e.g. if Authorization is defined twice, once with basic
            // and once with bearer
            SecurityException caught = null;
            Optional<String> result = Optional.empty();
            for (String header : tokenHeaders) {
                try {
                    result = Optional.of(headerExtractor.apply(header));
                } catch (SecurityException e) {
                    caught = e;
                }
            }
            if (result.isPresent()) {
                return result;
            }
            if (caught != null) {
                throw caught;
            }
            return result;
        } else {
            String tokenHeader = tokenHeaders.get(0);
            return Optional.of(headerExtractor.apply(tokenHeader));
        }
    }

    /**
     * Extracts the token from the string value of the header (or other field).
     *
     * @param tokenRawValue such as "bearer AAAAAAA"
     * @return token extracted based on the configured rules
     */
    public String extractToken(String tokenRawValue) {
        return headerExtractor.apply(tokenRawValue);
    }

    /**
     * Name of the header the token is expected in (or will be written into).
     * @return header name
     */
    public String tokenHeader() {
        return tokenHeader;
    }

    /**
     * Set the token as a new header.
     * Creates the header if not present, replaces header value if present.
     *
     * @param headers Headers to update
     * @param token   Token value
     */
    public void header(Map<String, List<String>> headers, String token) {
        headers.put(tokenHeader, singletonList(headerCreator.apply(token)));
    }

    /**
     * Add the token as a new header value.
     * Creates the header if not present, adds header value to list of values if present.
     *
     * @param headers Headers to update
     * @param token   Token value
     */
    public void addHeader(Map<String, List<String>> headers, String token) {
        String tokenValue = headerCreator.apply(token);

        List<String> values = headers.get(tokenHeader);

        if (null == values) {
            values = singletonList(tokenValue);
        } else {
            values = new ArrayList<>(values);
            values.add(tokenValue);
        }

        headers.put(tokenHeader, values);
    }

    /**
     * Fluent API builder to create {@link TokenHandler}.
     */
    @Configured
    public static final class Builder implements io.helidon.common.Builder<TokenHandler> {
        private String tokenHeader;
        private String tokenPrefix;
        private Pattern tokenPattern;
        private String tokenFormat;

        private Builder() {
        }

        /**
         * Set the name of header to look into to extract the token.
         *
         * @param header header name (such as Authorization), case insensitive
         * @return updated builder instance
         */
        @ConfiguredOption(key = "header")
        public Builder tokenHeader(String header) {
            Objects.requireNonNull(header);

            this.tokenHeader = header;
            return this;
        }

        /**
         * Set the prefix of header value to extract the token.
         *
         * @param prefix prefix of header value to strip from it, case insensitive
         * @return updated builder instance
         */
        @ConfiguredOption(key = "prefix")
        public Builder tokenPrefix(String prefix) {
            Objects.requireNonNull(prefix);

            this.tokenPrefix = prefix;
            return this;
        }

        /**
         * Set the token pattern (Regular expression) to extract the token.
         *
         * @param pattern pattern to use to extract the token, first group will be used
         * @return updated builder instance
         */
        @ConfiguredOption(key = "regexp", type = String.class)
        public Builder tokenPattern(Pattern pattern) {
            Objects.requireNonNull(pattern);

            this.tokenPrefix = null;
            this.tokenPattern = pattern;
            return this;
        }

        /**
         * Build a new instance from this builder.
         *
         * @return instance built based on this builder
         */
        @Override
        public TokenHandler build() {
            Objects.requireNonNull(tokenHeader, "Token header must be configured");
            return new TokenHandler(this);
        }

        /**
         * Update builder from config.
         *
         * @param config Configuration to update from
         * @return update builder instance
         */
        public Builder config(Config config) {
            config.get("header").asString().ifPresent(this::tokenHeader);
            config.get("prefix").asString().ifPresent(this::tokenPrefix);
            config.get("regexp").as(Pattern.class).ifPresent(this::tokenPattern);
            config.get("format").asString().ifPresent(this::tokenFormat);

            return this;
        }

        /**
         * Token format for creating outbound tokens.
         *
         * @param format Format according to {@link String#format(String, Object...)}, token will be a single string parameter
         * @return updated builder instance
         */
        @ConfiguredOption(key = "format")
        public Builder tokenFormat(String format) {
            this.tokenFormat = format;
            return this;
        }
    }
}
