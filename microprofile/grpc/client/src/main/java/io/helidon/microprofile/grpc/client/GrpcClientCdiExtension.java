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

package io.helidon.microprofile.grpc.client;

import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

import io.helidon.grpc.api.Grpc;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Annotated;
import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanAttributes;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import jakarta.enterprise.inject.spi.ProducerFactory;

/**
 * A CDI extension to add gRPC client functionality.
 */
public class GrpcClientCdiExtension implements Extension {

    private final Set<Type> proxyTypes = new HashSet<>();

    /**
     * Adds beans to the bean manager.
     *
     * @param event before bean discovery event
     */
    public void addBeans(@Observes BeforeBeanDiscovery event) {
        event.addAnnotatedType(ChannelProducer.class, ChannelProducer.class.getName());
    }

    /**
     * Process injection points.
     * <p>
     * In this method injection points that have the {@link io.helidon.grpc.api.Grpc.GrpcProxy} are processed
     * and their types are stored so that in the {@link #afterBean(
     *jakarta.enterprise.inject.spi.AfterBeanDiscovery, jakarta.enterprise.inject.spi.BeanManager)}
     * we can manually create a producer for the correct service proxy type.
     *
     * @param pip the injection point
     * @param <X> the declared type of the injection point.
     * @param <T> the bean class of the bean that declares the injection point
     */
    public <T, X> void gatherApplications(@Observes ProcessInjectionPoint<T, X> pip) {
        Annotated annotated = pip.getInjectionPoint().getAnnotated();
        if (annotated.isAnnotationPresent(Grpc.GrpcProxy.class)) {
            Type type = pip.getInjectionPoint().getType();
            proxyTypes.add(type);
        }
    }

    /**
     * Process the previously captured {@link io.helidon.grpc.api.Grpc.GrpcProxy} injection points.
     * <p>
     * For each {@link io.helidon.grpc.api.Grpc.GrpcProxy} injection point we create a producer bean
     * for the required type.
     *
     * @param event the {@link jakarta.enterprise.inject.spi.AfterBeanDiscovery} event
     * @param beanManager the CDI bean manager
     */
    public void afterBean(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
        AnnotatedType<GrpcProxyProducer> producerType = beanManager.createAnnotatedType(GrpcProxyProducer.class);
        AnnotatedMethod<? super GrpcProxyProducer> producerMethod = producerType.getMethods()
                .stream()
                .filter(m -> m.isAnnotationPresent(Grpc.GrpcProxy.class))
                .filter(m -> m.isAnnotationPresent(Grpc.GrpcChannel.class))
                .findFirst()
                .orElse(null);
        if (producerMethod != null) {
            for (Type type : proxyTypes) {
                addProducerBean(event, beanManager, producerMethod, type);
            }
        }
    }

    private void addProducerBean(AfterBeanDiscovery event,
                                 BeanManager beanManager,
                                 AnnotatedMethod<? super GrpcProxyProducer> producerMethod,
                                 Type type) {
        BeanAttributes<?> producerAttributes = beanManager.createBeanAttributes(producerMethod);
        ProducerFactory<GrpcProxyProducer> factory = beanManager.getProducerFactory(producerMethod, null);
        Set<Type> types = Set.of(Object.class, type);
        BeanAttributes<?> beanAttributes = DelegatingBeanAttributes.create(producerAttributes, types);
        event.addBean(beanManager.createBean(beanAttributes, GrpcProxyProducer.class, factory));
    }
}
