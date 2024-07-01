/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import java.lang.annotation.Annotation;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.microprofile.grpc.core.Grpc;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.GrpcService;
import io.helidon.webserver.grpc.GrpcTracingConfig;

import io.grpc.BindableService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A CDI extension that will discover and register gRPC routes.
 */
public class GrpcMpCdiExtension implements Extension {

    private static final Logger LOGGER = Logger.getLogger(GrpcMpCdiExtension.class.getName());

    private Config config;

    private void discoverRoutes(@Observes @Initialized(ApplicationScoped.class) Object event, BeanManager beanManager) {
        config = MpConfig.toHelidonConfig(ConfigProvider.getConfig());
        GrpcRouting.Builder routingBuilder = discoverGrpcRouting(beanManager);
        loadExtensions(beanManager, config, routingBuilder);
        ServerCdiExtension extension = beanManager.getExtension(ServerCdiExtension.class);
        extension.addRouting(routingBuilder);
    }

    /**
     * Discover the services and interceptors to use to configure the {@link GrpcRouting}.
     *
     * @param beanManager the CDI bean manager
     * @return the {@link GrpcRouting} to use or {@code null} if no services
     * or routing were discovered
     */
    private GrpcRouting.Builder discoverGrpcRouting(BeanManager beanManager) {
        Instance<Object> instance = beanManager.createInstance();
        GrpcRouting.Builder builder = GrpcRouting.builder();

        // discover @Grpc annotated beans
        // we use the bean manager to do this as we need the actual bean class
        beanManager.getBeans(Object.class, Any.Literal.INSTANCE)
                .stream()
                .filter(this::hasGrpcQualifier)
                .forEach(bean -> {
                    Class<?> beanClass = bean.getBeanClass();
                    Annotation[] qualifiers = bean.getQualifiers().toArray(new Annotation[0]);
                    Object service = instance.select(beanClass, qualifiers).get();
                    register(service, builder, beanClass, beanManager);
                });

        // discover beans of type GrpcService
        beanManager.getBeans(GrpcService.class)
                .forEach(bean -> {
                    Class<?> beanClass = bean.getBeanClass();
                    Annotation[] qualifiers = bean.getQualifiers().toArray(new Annotation[0]);
                    Object service = instance.select(beanClass, qualifiers).get();
                    builder.service((GrpcService) service);
                });

        // discover beans of type BindableService
        beanManager.getBeans(BindableService.class)
                .forEach(bean -> {
                    Class<?> beanClass = bean.getBeanClass();
                    Annotation[] qualifiers = bean.getQualifiers().toArray(new Annotation[0]);
                    Object service = instance.select(beanClass, qualifiers).get();
                    builder.service((BindableService) service);
                });

        return builder;
    }

    private boolean hasGrpcQualifier(Bean<?> bean) {
        return bean.getQualifiers()
                .stream()
                .anyMatch(q -> Grpc.class.isAssignableFrom(q.annotationType()));
    }

    /**
     * Register the service with the routing.
     * <p>
     * The service is actually a CDI proxy so the real service.
     *
     * @param service the service to register
     * @param builder the gRPC routing
     * @param beanManager the {@link BeanManager} to use to locate beans required by the service
     */
    private void register(Object service, GrpcRouting.Builder builder, Class<?> cls, BeanManager beanManager) {
        GrpcServiceBuilder serviceBuilder = GrpcServiceBuilder.create(cls, () -> service, beanManager);
        if (serviceBuilder.isAnnotatedService()) {
            GrpcTracingConfig tracingConfig = GrpcTracingConfig.create(config.get("tracing.grpc"));
            builder.service(serviceBuilder.build(), tracingConfig);
        } else {
            LOGGER.log(Level.WARNING,
                    () -> "Discovered type is not a properly annotated gRPC service " + service.getClass());
        }
    }

    /**
     * Load any instances of {@link GrpcMpExtension} discovered by the {@link ServiceLoader}
     * and allow them to further configure the gRPC server.
     *
     * @param beanManager the {@link BeanManager}
     * @param config the Helidon configuration
     * @param routingBuilder the {@link GrpcRouting.Builder}
     */
    private void loadExtensions(BeanManager beanManager,
                                Config config,
                                GrpcRouting.Builder routingBuilder) {
        GrpcMpContext context = new GrpcMpContext() {
            @Override
            public Config config() {
                return config;
            }

            @Override
            public GrpcRouting.Builder routing() {
                return routingBuilder;
            }

            @Override
            public BeanManager beanManager() {
                return beanManager;
            }
        };

        HelidonServiceLoader.create(ServiceLoader.load(GrpcMpExtension.class))
                            .forEach(ext -> ext.configure(context));
        beanManager.createInstance()
                .select(GrpcMpExtension.class)
                .stream()
                .forEach(ext -> ext.configure(context));
    }
}
