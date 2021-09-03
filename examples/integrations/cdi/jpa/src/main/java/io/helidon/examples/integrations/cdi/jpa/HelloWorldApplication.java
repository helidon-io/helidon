/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.cdi.jpa;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.core.Application;

/**
 * An example {@link Application} demonstrating the modular
 * integration of JPA and JTA with Helidon MicroProfile.
 */
@ApplicationScoped
public class HelloWorldApplication extends Application {

    private final Set<Class<?>> classes;

    /**
     * Creates a new {@link HelloWorldApplication}.
     */
    public HelloWorldApplication() {
        super();
        final Set<Class<?>> classes = new HashSet<>();
        classes.add(HelloWorldResource.class);
        classes.add(JPAExceptionMapper.class);
        this.classes = Collections.unmodifiableSet(classes);
    }

    /**
     * Returns a non-{@code null} {@link Set} of {@link Class}es that
     * comprise this JAX-RS application.
     *
     * @return a non-{@code null}, {@linkplain
     * Collections#unmodifiableSet(Set) unmodifiable <code>Set</code>}
     *
     * @see HelloWorldResource
     */
    @Override
    public Set<Class<?>> getClasses() {
        return this.classes;
    }

}
