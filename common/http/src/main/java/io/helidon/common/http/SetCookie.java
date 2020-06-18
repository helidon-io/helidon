/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.http;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Represents {@code 'Set-Cookie'} header value specified by <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
 *
 * <p>It is mutable and fluent builder.
 */
public class SetCookie {

    private static final String PARAM_SEPARATOR = "; ";

    private final String name;
    private final String value;
    private final ZonedDateTime expires;
    private final Duration maxAge;
    private final String domain;
    private final String path;
    private final boolean secure;
    private final boolean httpOnly;

    private SetCookie(Builder builder) {
        this.name = builder.name;
        this.value = builder.value;
        this.expires = builder.expires;
        this.maxAge = builder.maxAge;
        this.domain = builder.domain;
        this.path = builder.path;
        this.secure = builder.secure;
        this.httpOnly = builder.httpOnly;
    }

    /**
     * Creates a new fluent API builder.
     *
     * @param name  a cookie name.
     * @param value a cookie value.
     * @return a new fluent API builder
     */
    public static Builder builder(String name, String value) {
        return new Builder(name, value);
    }

    /**
     * Creates new instance.
     *
     * @param name  a cookie name.
     * @param value a cookie value.
     * @return a new instance with just the name and value configured
     */
    public static SetCookie create(String name, String value) {
        return builder(name, value)
                .build();
    }

    /**
     * Returns content of this instance as a 'Set-Cookie:' header value specified
     * by <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
     *
     * @return a 'Set-Cookie:' header value.
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(name).append('=').append(value);
        if (expires != null) {
            result.append(PARAM_SEPARATOR);
            result.append("Expires=");
            result.append(expires.format(Http.DateTime.RFC_1123_DATE_TIME));
        }
        if ((maxAge != null) && !maxAge.isNegative() && !maxAge.isZero()) {
            result.append(PARAM_SEPARATOR);
            result.append("Max-Age=");
            result.append(maxAge.getSeconds());
        }
        if (domain != null) {
            result.append(PARAM_SEPARATOR);
            result.append("Domain=");
            result.append(domain);
        }
        if (path != null) {
            result.append(PARAM_SEPARATOR);
            result.append("Path=");
            result.append(path);
        }
        if (secure) {
            result.append(PARAM_SEPARATOR);
            result.append("Secure");
        }
        if (httpOnly) {
            result.append(PARAM_SEPARATOR);
            result.append("HttpOnly");
        }
        return result.toString();
    }

    /**
     * A fluent API builder for {@link SetCookie}.
     */
    public static final class Builder implements io.helidon.common.Builder<SetCookie> {
        private final String name;
        private final String value;
        private ZonedDateTime expires;
        private Duration maxAge;
        private String domain;
        private String path;
        private boolean secure;
        private boolean httpOnly;

        private Builder(String name, String value) {
            Objects.requireNonNull(name, "Parameter 'name' is null!");
            //todo validate accepted characters
            this.name = name;
            this.value = value;
        }

        @Override
        public SetCookie build() {
            return new SetCookie(this);
        }

        /**
         * Sets {@code Expires} parameter.
         *
         * @param expires an {@code Expires} parameter.
         * @return Updated instance.
         */
        public Builder expires(ZonedDateTime expires) {
            this.expires = expires;
            return this;
        }

        /**
         * Sets {@code Expires} parameter.
         *
         * @param expires an {@code Expires} parameter.
         * @return Updated instance.
         */
        public Builder expires(Instant expires) {
            if (expires == null) {
                this.expires = null;
            } else {
                this.expires = ZonedDateTime.ofInstant(expires, ZoneId.systemDefault());
            }
            return this;
        }

        /**
         * Sets {@code Max-Age} parameter.
         *
         * @param maxAge an {@code Max-Age} parameter.
         * @return Updated instance.
         */
        public Builder maxAge(Duration maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         * Sets {@code Domain} parameter.
         *
         * @param domain an {@code Domain} parameter.
         * @return Updated instance.
         */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Sets {@code Path} parameter.
         *
         * @param path an {@code Path} parameter.
         * @return Updated instance.
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets {@code Domain} and {@code Path} parameters.
         *
         * @param domainAndPath an URI to specify {@code Domain} and {@code Path} parameters.
         * @return Updated instance.
         */
        public Builder domainAndPath(URI domainAndPath) {
            if (domainAndPath == null) {
                this.domain = null;
                this.path = null;
            } else {
                this.domain = domainAndPath.getHost();
                this.path = domainAndPath.getPath();
            }
            return this;
        }

        /**
         * Sets {@code Secure} parameter.
         *
         * @param secure an {@code Secure} parameter.
         * @return Updated instance.
         */
        public Builder secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Sets {@code HttpOnly} parameter.
         *
         * @param httpOnly an {@code HttpOnly} parameter.
         * @return Updated instance.
         */
        public Builder httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }
    }
}
