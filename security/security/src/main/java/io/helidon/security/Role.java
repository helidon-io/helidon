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

/**
 * A security role used in RBAC (role based access control) schemes.
 */
public final class Role extends Grant {
    /**
     * Type of grant used in {@link Grant#type()}.
     */
    public static final String ROLE_TYPE = "role";

    private Role(Builder builder) {
        super(builder);
    }

    /**
     * Create a role based on a name without any attributes.
     *
     * @param name name of role
     * @return a new role instance
     */
    public static Role create(String name) {
        return builder().name(name).build();
    }

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A fluent API builder for {@link Role}.
     */
    public static final class Builder extends Grant.Builder<Builder> {
        private Builder() {
            type(ROLE_TYPE);
        }

        @Override
        public Role build() {
            return new Role(this);
        }
    }
}
