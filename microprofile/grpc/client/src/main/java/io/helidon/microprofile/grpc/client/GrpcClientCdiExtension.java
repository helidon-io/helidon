/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanAttributes;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessInjectionPoint;
import javax.enterprise.inject.spi.ProducerFactory;

import io.helidon.microprofile.grpc.core.InProcessGrpcChannel;

/**
 * A CDI extension to add gRPC client functionality.
 */
public class GrpcClientCdiExtension implements Extension {

    private final Set<Type> proxyTypes = new HashSet<>();
    private final Set<Type> inProcessProxyTypes = new HashSet<>();

    /**
     * Process injection points.
     * <p>
     * In this method all of the injection points that have the {@link GrpcProxy} are processed
     * and their types are stored so that in the {@link #afterBean(AfterBeanDiscovery, BeanManager)}
     * we can manually create a producer for the correct service proxy type.
     *
     * @param pip  the injection point
     * @param <X> the declared type of the injection point.
     * @param <T> the bean class of the bean that declares the injection point
     */
    public <T, X> void gatherApplications(@Observes ProcessInjectionPoint<T, X> pip) {
        Annotated annotated = pip.getInjectionPoint().getAnnotated();
        if (annotated.isAnnotationPresent(GrpcProxy.class)) {
            Type type = pip.getInjectionPoint().getType();

            // ToDo: verify that the type is an interface - how are we supposed to signal errors?

            if (annotated.isAnnotationPresent(InProcessGrpcChannel.class)) {
                inProcessProxyTypes.add(type);
            } else {
                proxyTypes.add(type);
            }
        }
    }

    /**
     * Process the previously captured {@link GrpcProxy} injection points.
     * <p>
     * For each {@link GrpcProxy} injection point we create a producer bean
     * for the required type.
     *
     * @param event the {@link AfterBeanDiscovery} event
     * @param beanManager the CDI bean manager
     */
    public void afterBean(@Observes AfterBeanDiscovery event, BeanManager beanManager) {
        AnnotatedType<GrpcProxyProducer> producerType = beanManager.createAnnotatedType(GrpcProxyProducer.class);
        AnnotatedMethod<? super GrpcProxyProducer> producerMethod = producerType.getMethods()
                .stream()
                .filter(m -> m.isAnnotationPresent(GrpcProxy.class))
                .filter(m -> m.isAnnotationPresent(GrpcChannel.class))
                .findFirst()
                .get();

        AnnotatedMethod<? super GrpcProxyProducer> inProcessMethod = producerType.getMethods()
                .stream()
                .filter(m -> m.isAnnotationPresent(GrpcProxy.class))
                .filter(m -> m.isAnnotationPresent(InProcessGrpcChannel.class))
                .findFirst()
                .get();

        for (Type type : proxyTypes) {
            addProducerBean(event, beanManager, producerMethod, type);
        }

        for (Type type : inProcessProxyTypes) {
            addProducerBean(event, beanManager, inProcessMethod, type);
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
