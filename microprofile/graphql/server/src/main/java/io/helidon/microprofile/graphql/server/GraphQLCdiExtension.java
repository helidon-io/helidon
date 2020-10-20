/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Type;

/**
 * A CDI {@link Extension} to collect the classes that are of interest to Microprofile GraphQL.
 */
public class GraphQLCdiExtension implements Extension {

    /**
     * The {@link List} of collected API's.
     */
    private final List<Class<?>> collectedApis = new ArrayList<>();

    /**
     * The {@link List} of collected Schema processors.
     */
    private final List<Class<?>> collectedProcessors = new ArrayList<>();

    /**
     * Collect the classes that have the following Microprofile GraphQL annotations.
     *
     * @param processAnnotatedType annotation types to process
     */
    void collectApis(@Observes @WithAnnotations({GraphQLApi.class,
                                                        Type.class,
                                                        Input.class,
                                                        Interface.class }) ProcessAnnotatedType<?> processAnnotatedType) {
        this.collectedApis.add(processAnnotatedType.getAnnotatedType().getJavaClass());
    }

    /**
     * Collect the classes that have the {@link SchemaProcessor} annotation.
     *
     * @param processAnnotatedType annotation types to process
     */
    void collectProcessors(@Observes @WithAnnotations(SchemaProcessor.class) ProcessAnnotatedType<?> processAnnotatedType) {
        this.collectedProcessors.add(processAnnotatedType.getAnnotatedType().getJavaClass());
    }

    /**
     * Returns the collected API's.
     *
     * @return the collected API's
     */
    public Class<?>[] collectedApis() {
        return collectedApis.toArray(new Class[0]);
    }

    /**
     * Returns the collected Schema Processors.
     *
     * @return the collected Schema Processors.
     */
    public Class<?>[] collectedSchemaProcessors() {
        return collectedProcessors.toArray(new Class[0]);
    }
}
