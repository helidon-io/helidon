/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.rsocket.cdi;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.microprofile.cdi.RuntimeStart;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.Routing;
import io.helidon.webserver.rsocket.FireAndForgetHandler;
import io.helidon.webserver.rsocket.RSocketEndpoint;
import io.helidon.webserver.rsocket.RSocketSupport;
import io.helidon.webserver.rsocket.RequestChannelHandler;
import io.helidon.webserver.rsocket.RequestResponseHandler;
import io.helidon.webserver.rsocket.RequestStreamHandler;
import io.helidon.webserver.rsocket.server.RSocketRouting;
import io.rsocket.Payload;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import static javax.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Configure RSocket related things.
 */
public class RSocketCdiExtension implements Extension {
    private static final Logger LOGGER = Logger.getLogger(RSocketCdiExtension.class.getName());

    private static final String DEFAULT_RSOCKET_PATH = "/";

    private Config config;

    private ServerCdiExtension serverCdiExtension;

    RSocketRouting.Builder rSocketRoutingBuilder = RSocketRouting.builder();


    private void prepareRuntime(@Observes @RuntimeStart Config config) {
        this.config = config;
    }

    private void startServer(@Observes @Priority(PLATFORM_AFTER + 99) @Initialized(ApplicationScoped.class) Object event,
                             BeanManager beanManager) {
        serverCdiExtension = beanManager.getExtension(ServerCdiExtension.class);
        registerRSockets();
    }

    private void registerRSockets() {
        RSocketRouting rSocketRouting = rSocketRoutingBuilder.build();

        LOGGER.info("ROUTING: "+rSocketRouting.toString());

        serverCdiExtension.serverRoutingBuilder().register("/rsocket",
                RSocketSupport.builder()
                        .register(RSocketEndpoint.create(rSocketRouting, "/board")
                                .getEndPoint()
                        ).build());
    }


    /**
     * Collect annotated endpoints.
     *
     * @param endpoint The endpoint.
     */
    private void endpointClasses(@Observes @WithAnnotations(RSocket.class) ProcessAnnotatedType<?> endpoint) {
        LOGGER.info(() -> "Annotated endpoint found " + endpoint.getAnnotatedType().getJavaClass());

        LOGGER.finest("Methods:");
        List<Method> methods = endpoint.getAnnotatedType().getMethods().stream().map(AnnotatedMethod::getJavaMember).collect(Collectors.toList());


        for (Method method : methods) {
            LOGGER.finest("Method: " + method.getName());
            LOGGER.finest("Has the following annotations");
            for (Annotation annotation : method.getAnnotations()) {
                LOGGER.finest(" - " + annotation.toString());

                if (annotation.annotationType().equals(FireAndForget.class)) {
                    rSocketRoutingBuilder.fireAndForget(
                            payload -> {
                                try {
                                    return (Single<Void>) method.invoke(payload);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    LOGGER.severe(e.toString());
                                }
                                return Single.empty();
                            });
                } else if (annotation.annotationType().equals(RequestChannel.class)) {
                    rSocketRoutingBuilder.requestChannel(payloads -> {
                        try {
                            return (Multi<Payload>) method.invoke(payloads);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            LOGGER.severe(e.toString());
                        }
                        return Multi.empty();
                    });
                } else if (annotation.annotationType().equals(RequestResponse.class)) {
                    rSocketRoutingBuilder.requestResponse(payload -> {
                        try {
                            return (Single<Payload>) method.invoke(payload);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            LOGGER.severe(e.toString());
                        }
                        return Single.empty();
                    });
                } else if (annotation.annotationType().equals(RequestStream.class)) {
                    rSocketRoutingBuilder.requestStream(payload -> {
                        try {
                            return (Multi<Payload>) method.invoke(payload);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            LOGGER.severe(e.toString());
                        }
                        return Multi.empty();
                    });
                }
            }
        }
        LOGGER.info("Endpoints discovery completed");
    }
}
