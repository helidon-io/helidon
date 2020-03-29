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

package io.helidon.security;

import io.helidon.security.util.AbacSupport;

/**
 * A security principal.
 *
 * @see java.security.Principal
 */
public interface Principal extends AbacSupport, java.security.Principal {

    /**
     * Id of this principal.
     *
     * @return id if defined, name otherwise
     */
    String id();

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @return a builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a principal instance for an id (or name).
     *
     * @param id identification used both for name and id attributes of this principal
     * @return a new principal with the specified id (and name)
     */
    static Principal create(String id) {
        return Principal.builder()
                .id(id)
                .build();
    }

    /**
     * A fluent API builder for {@link Principal}.
     */
    final class Builder implements io.helidon.common.Builder<Principal> {
        private String name;
        private String id;
        private BasicAttributes properties = BasicAttributes.create();

        private Builder() {
        }

        String name() {
            return name;
        }

        String id() {
            return id;
        }

        BasicAttributes properties() {
            return properties;
        }

        @Override
        public Principal build() {
            return new HelidonPrincipal(this);
        }

        /**
         * Principal name.
         *
         * @param name name of the principal (e.g. "John Doe"), also used as id, unless explicitly defined
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            if (null == id) {
                id = name;
            }
            return this;
        }

        /**
         * Principal id.
         *
         * @param id id of the principal (e.g. "oid-user-55d45d5sd4"), also used as name, unless explicitly defined
         * @return updated builder instance
         */
        public Builder id(String id) {
            this.id = id;
            if (null == name) {
                name = id;
            }
            return this;
        }

        /**
         * Custom attributes of this principal.
         *
         * @param attributes attributes to add
         * @return updated builder instance
         */
        private Builder attributes(AbacSupport attributes) {
            this.properties = BasicAttributes.create(attributes);
            return this;
        }

        /**
         * Add a custom attribute to this principal.
         *
         * @param key   name of the attribute
         * @param value value of the attribute, may be null (in that case the key is not added as an attribute)
         * @return updated builder instance
         */
        public Builder addAttribute(String key, Object value) {
            if (null == value) {
                return this;
            }
            this.properties.put(key, value);
            return this;
        }
    }

}
