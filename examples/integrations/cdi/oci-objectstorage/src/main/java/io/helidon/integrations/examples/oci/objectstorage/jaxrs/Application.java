/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.examples.oci.objectstorage.jaxrs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;

/**
 * A JAX-RS {@linkplain javax.ws.rs.core.Application application} in {@linkplain ApplicationScoped application scope}.
 *
 * @see #getClasses()
 */
@ApplicationScoped
@ApplicationPath("/")
public class Application extends javax.ws.rs.core.Application {

    private final Set<Class<?>> classes;

    /**
     * Creates a new {@link Application}.
     */
    public Application() {
        super();
        final Set<Class<?>> classes = new HashSet<>();
        classes.add(HelidonLogoResource.class);
        this.classes = Collections.unmodifiableSet(classes);
    }

    /**
     * Returns a non-{@code null} {@linkplain
     * java.util.Collections#unmodifiableSet(Set) immutable
     * <code>Set</code>} of {@link Class}es that comprise this JAX-RS application.
     *
     * @return a non-{@code null} {@linkplain
     * java.util.Collections#unmodifiableSet(Set) immutable
     * <code>Set</code>}
     */
    @Override
    public Set<Class<?>> getClasses() {
        return this.classes;
    }

}
