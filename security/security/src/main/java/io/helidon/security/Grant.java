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

package io.helidon.security;

import java.security.Principal;
import java.util.Collection;
import java.util.Objects;

import io.helidon.security.util.AbacSupport;

/**
 * A concept representing anything that can be granted to a subject.
 * This may be:
 * <ul>
 * <li>role</li>
 * <li>scope</li>
 * <li>permission</li>
 * <li>anything else grantable, including additional principals</li>
 * </ul>
 */
public class Grant implements AbacSupport, Principal {
    private final AbacSupport properties;
    private final String type;
    private final String name;
    private final String origin;

    /**
     * Create an instance based on a builder.
     *
     * @param builder builder instance
     */
    protected Grant(Builder<?> builder) {
        this.type = builder.type;
        this.name = builder.name;
        this.origin = builder.origin;

        BasicAttributes properties = BasicAttributes.create(builder.properties);
        properties.put("type", type);
        properties.put("name", name);
        properties.put("origin", origin);
        this.properties = properties;
    }

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @return builder instance
     */
    public static Builder<?> builder() {
        return new Builder<>();
    }

    @Override
    public Object abacAttributeRaw(String key) {
        return properties.abacAttributeRaw(key);
    }

    @Override
    public Collection<String> abacAttributeNames() {
        return properties.abacAttributeNames();
    }

    /**
     * Type of this grant.
     * Known types:
     * <ul>
     * <li>"role" - represents a role grant, also a dedicated class is created for this type: {@link Role}</li>
     * <li>"scope" - represents a OAuth2 scope grant</li>
     * </ul>
     *
     * @return type of the grant
     */
    public String type() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return type + ":" + name;
    }

    /**
     * Origin of this grant - this may be an arbitrary string, URI, component creating it etc.
     * @return origin of this grant
     */
    public String origin() {
        return origin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Grant)) {
            return false;
        }
        Grant grant = (Grant) o;
        return type.equals(grant.type)
                && getName().equals(grant.getName())
                && origin.equals(grant.origin);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, getName(), origin);
    }

    /**
     * A fluent API builder for {@link Grant} to be extended by other {@link Grant} implementations.
     *
     * @param <T> type of the builder, needed for builder inheritance
     */
    public static class Builder<T extends Builder<T>> implements io.helidon.common.Builder<Grant> {
        private final T instance;

        private BasicAttributes properties = BasicAttributes.create();
        private String type;
        private String name;
        private String origin = "builder";

        /**
         * Create a new instance.
         */
        @SuppressWarnings("unchecked")
        protected Builder() {
            this.instance = (T) this;
        }

        @Override
        public Grant build() {
            return new Grant(this);
        }

        /**
         * Configure type of this grant.
         *
         * @param type type name, known types are "role" and "scope"
         * @return updated builder instance
         */
        public T type(String type) {
            this.type = type;
            return instance;
        }

        /**
         * Name of this grant.
         *
         * @param name logical name of this grant (e.g. "admin", "calendar_read" etc.)
         * @return updated builder instance
         */
        public T name(String name) {
            this.name = name;
            return instance;
        }

        /**
         * Origin of this grant (e.g. name of a system).
         *
         * @param origin who granted this grant?
         * @return updated builder instance
         */
        public T origin(String origin) {
            this.origin = origin;
            return instance;
        }

        /**
         * Attributes of this grant.
         *
         * @param attribs Attributes to add to this grant, allowing us to extend the information known (such as "nickname",
         *                "cn" etc.)
         * @return updated builder instance
         */
        public T attributes(AbacSupport attribs) {
            this.properties = BasicAttributes.create(attribs);
            return instance;
        }

        /**
         * Add and attribute to this grant.
         *
         * @param key   name of the attribute
         * @param value value of the attribute
         * @return updated builder instance
         */
        public T addAttribute(String key, Object value) {
            this.properties.put(key, value);
            return instance;
        }
    }
}
