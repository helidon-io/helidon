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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.ProcessManagedBean;
import javax.enterprise.inject.spi.WithAnnotations;

import io.helidon.graphql.server.GraphQlSupport;
import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;

import graphql.schema.GraphQLSchema;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.graphql.ConfigKey;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Input;
import org.eclipse.microprofile.graphql.Interface;
import org.eclipse.microprofile.graphql.Type;

import static javax.interceptor.Interceptor.Priority.LIBRARY_BEFORE;

/**
 * A CDI {@link Extension} to collect the classes that are of interest to Microprofile GraphQL.
 */
public class GraphQlCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(GraphQlCdiExtension.class.getName());

    /**
     * The {@link List} of collected API's.
     */
    private final Set<Class<?>> candidateApis = new HashSet<>();
    private final Set<Class<?>> collectedApis = new HashSet<>();

    /**
     * Collect the classes that have the following Microprofile GraphQL annotations.
     *
     * @param processAnnotatedType annotation types to process
     */
    void collectCandidateApis(@Observes @WithAnnotations(GraphQLApi.class) ProcessAnnotatedType<?> processAnnotatedType) {
        Class<?> javaClass = processAnnotatedType.getAnnotatedType().getJavaClass();
        this.candidateApis.add(javaClass);
        if (javaClass.isInterface()) {
            collectedApis.add(javaClass);
        }
    }

    void collectApis(@Observes @WithAnnotations({Type.class, Input.class,
                                                        Interface.class}) ProcessAnnotatedType<?> processAnnotatedType) {
        // these are directly added
        this.collectedApis.add(processAnnotatedType.getAnnotatedType().getJavaClass());
    }

    void collectNonVetoed(@Observes ProcessManagedBean<?> event) {
        AnnotatedType<?> type = event.getAnnotatedBeanClass();
        Class<?> clazz = type.getJavaClass();

        if (candidateApis.remove(clazz)) {
            collectedApis.add(clazz);
        }
    }

    void addGraphQlBeans(@Observes BeforeBeanDiscovery event) {
        event.addAnnotatedType(GraphQlBean.class, GraphQlBean.class.getName())
                .add(ApplicationScoped.Literal.INSTANCE);
    }

    void clearCandidates(@Observes AfterBeanDiscovery event) {
        candidateApis.clear();
    }

    void registerWithWebServer(@Observes @Priority(LIBRARY_BEFORE + 9) @Initialized(ApplicationScoped.class) Object event,
                               BeanManager bm) {

        Config config = ConfigProvider.getConfig();
        // this works for Helidon MP config
        io.helidon.config.Config graphQlConfig = ((io.helidon.config.Config) config).get("graphql");

        InvocationHandler.Builder handlerBuilder = InvocationHandler.builder()
                .config(graphQlConfig)
                .schema(createSchema());

        config.getOptionalValue(ConfigKey.DEFAULT_ERROR_MESSAGE, String.class)
                .ifPresent(handlerBuilder::defaultErrorMessage);

        config.getOptionalValue(ConfigKey.EXCEPTION_WHITE_LIST, String[].class)
                .ifPresent(handlerBuilder::exceptionWhitelist);

        config.getOptionalValue(ConfigKey.EXCEPTION_BLACK_LIST, String[].class)
                .ifPresent(handlerBuilder::exceptionBlacklist);

        GraphQlSupport graphQlSupport = GraphQlSupport.builder()
                .config(graphQlConfig)
                .invocationHandler(handlerBuilder)
                .build();
        try {
            ServerCdiExtension server = bm.getExtension(ServerCdiExtension.class);
            Optional<String> routingNameConfig = config.getOptionalValue("graphql.routing", String.class);

            Routing.Builder routing = routingNameConfig.stream()
                    .filter(Predicate.not("@default"::equals))
                    .map(server::serverNamedRoutingBuilder)
                    .findFirst()
                    .orElseGet(server::serverRoutingBuilder);

            graphQlSupport.update(routing);
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Failed to set up routing with web server, maybe server extension missing?", e);
        }
    }

    Set<Class<?>> collectedApis() {
        return collectedApis;
    }

    private GraphQLSchema createSchema() {
        try {
            return SchemaGenerator.builder()
                    .classes(collectedApis)
                    .build()
                    .generateSchema()
                    .generateGraphQLSchema();
        } catch (Exception e) {
            throw new DeploymentException("Failed to set up graphQL", e);
        }
    }
}
