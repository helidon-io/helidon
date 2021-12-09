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

import java.util.Collection;

import io.helidon.security.util.AbacSupport;

/**
 * Default implementation of {@link Principal}.
 */
class HelidonPrincipal implements Principal {
    private final AbacSupport properties;
    private final String name;
    private final String id;

    HelidonPrincipal(Builder builder) {
        this.name = builder.name();
        this.id = builder.id();
        BasicAttributes container = BasicAttributes.create(builder.properties());

        container.put("name", name);
        container.put("id", id);
        this.properties = container;
    }

    @Override
    public Object abacAttributeRaw(String key) {
        return properties.abacAttributeRaw(key);
    }

    @Override
    public Collection<String> abacAttributeNames() {
        return properties.abacAttributeNames();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return "Principal{"
                + "properties=" + properties
                + ", name='" + name + '\''
                + ", id='" + id + '\''
                + '}';
    }

}
