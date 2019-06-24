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
package io.helidon.integrations.cdi.oci.objectstorage;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessBean;
import javax.inject.Provider;

import io.helidon.integrations.cdi.oci.common.MicroProfileConfigAuthenticationDetailsProvider;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.streaming.Stream;
import com.oracle.bmc.streaming.StreamClient;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * An {@link Extension} that integrates the {@link Stream} interface
 * into CDI-based applications.
 */
public class OCIStreamingExtension implements Extension {

    private final Set<Set<Annotation>> streamQualifiers;

    /**
     * Creates a new {@link OCIStreamingExtension}.
     */
    public OCIStreamingExtension() {
        super();
        this.streamQualifiers = new HashSet<>();
    }

    private <T> void discoverStreamInjectionPoints(@Observes final ProcessBean<T> event) {
        assert event != null;
        final Bean<?> bean = event.getBean();
        if (bean != null) {
            final Set<InjectionPoint> injectionPoints = bean.getInjectionPoints();
            if (injectionPoints != null && !injectionPoints.isEmpty()) {
                for (final InjectionPoint injectionPoint : injectionPoints) {
                    if (injectionPoint != null) {
                        final Set<Annotation> qualifiers = extractRelevantQualifiers(injectionPoint);
                        if (qualifiers != null && !qualifiers.isEmpty()) {
                            this.streamQualifiers.add(qualifiers);
                        }
                    }
                }
            }
        }
    }

    private void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
        if (event != null && beanManager != null && !this.streamQualifiers.isEmpty()) {
            final Config config = ConfigProvider.getConfig();
            assert config != null;
            final String region = config.getOptionalValue("oci.streaming.region", String.class)
                .orElse(config.getValue("oci.region", String.class));
            for (final Set<Annotation> qualifiers : this.streamQualifiers) {
                assert qualifiers != null;
                final Annotation[] qualifiersArray = qualifiers.toArray(new Annotation[qualifiers.size()]);
                if (noBeans(beanManager, Stream.class, qualifiersArray)) {
                    event.<Stream>addBean()
                        .scope(ApplicationScoped.class)
                        .addTransitiveTypeClosure(StreamClient.class)
                        .beanClass(StreamClient.class)
                        .addQualifiers(qualifiers)
                        .createWith(cc -> {

                                // Get a StreamClient.Builder one way or another.
                                final StreamClient.Builder builder;
                                Set<Bean<?>> beans = beanManager.getBeans(StreamClient.Builder.class, qualifiersArray);
                                if (beans == null || beans.isEmpty()) {
                                    beans = beanManager.getBeans(StreamClient.Builder.class);
                                }
                                if (beans == null || beans.isEmpty()) {
                                    builder = StreamClient.builder();
                                    assert builder != null;
                                    // Permit customization.
                                    beanManager.getEvent().select(StreamClient.Builder.class, qualifiersArray).fire(builder);
                                } else {
                                    final Bean<?> bean = beanManager.resolve(beans);
                                    assert bean != null;
                                    builder =
                                        (StreamClient.Builder)
                                        beanManager.getReference(bean,
                                                                 StreamClient.Builder.class,
                                                                 cc);
                                }
                                assert builder != null;

                                // Get an AuthenticationDetailsProvider one way or another.
                                final AuthenticationDetailsProvider authProvider;
                                beans = beanManager.getBeans(AuthenticationDetailsProvider.class, qualifiersArray);
                                if (beans == null || beans.isEmpty()) {
                                    beans = beanManager.getBeans(AuthenticationDetailsProvider.class);
                                }
                                if (beans == null || beans.isEmpty()) {
                                    authProvider = new MicroProfileConfigAuthenticationDetailsProvider(config);
                                    // Nothing to configure (no setter methods) so don't fire an event.
                                } else {
                                    final Bean<?> bean = beanManager.resolve(beans);
                                    assert bean != null;
                                    authProvider =
                                        (AuthenticationDetailsProvider)
                                        beanManager.getReference(bean,
                                                                 AuthenticationDetailsProvider.class,
                                                                 cc);
                                }
                                assert authProvider != null;

                                // Finally create the StreamClient
                                // using the raw materials we just
                                // gathered.
                                final StreamClient streamClient = builder.build(authProvider);
                                assert streamClient != null;
                                streamClient.setRegion(region);
                                return streamClient;
                            })
                        .destroyWith((streamClient, cc) -> {
                                try {
                                    streamClient.close();
                                } catch (final RuntimeException runtimeException) {
                                    throw runtimeException;
                                } catch (final Exception exception) {
                                    throw new DeploymentException(exception.getMessage(), exception);
                                }
                            });
                }
            }
        }
        this.streamQualifiers.clear();
    }

    private static Set<Annotation> extractRelevantQualifiers(final InjectionPoint injectionPoint) {
        return extractRelevantQualifiers(injectionPoint::getQualifiers,
                                         injectionPoint.getType(),
                                         Stream.class,
                                         new HashSet<>());
    }

    private static Set<Annotation> extractRelevantQualifiers(final Supplier<? extends Set<Annotation>> qualifiersSupplier,
                                                             final Type type,
                                                             final Class<?> testClass,
                                                             Set<Type> seen) {
        Objects.requireNonNull(qualifiersSupplier);
        Objects.requireNonNull(type);
        Objects.requireNonNull(testClass);
        if (seen == null) {
            seen = new HashSet<>();
        }
        Set<Annotation> returnValue = null;
        if (!seen.contains(type)) {
            seen.add(type);
            if (type instanceof Class) {
                if (testClass.isAssignableFrom((Class<?>) type)) {
                    returnValue = qualifiersSupplier.get();
                }
            } else if (type instanceof ParameterizedType) {
                final ParameterizedType parameterizedType = (ParameterizedType) type;
                final Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class) {
                    final Class<?> rawClass = (Class<?>) rawType;
                    if (Provider.class.isAssignableFrom(rawClass)) {
                        final Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
                            // e.g. Provider<Frob>, Provider<?
                            // extends Blatz>, Instance<Groo>,
                            // etc.
                            returnValue =
                                extractRelevantQualifiers(qualifiersSupplier,
                                                          actualTypeArguments[0],
                                                          testClass,
                                                          seen); // RECURSIVE
                        }
                    }
                }
            } else if (type instanceof WildcardType) {
                final WildcardType wildcardType = (WildcardType) type;
                final Type[] upperBounds = wildcardType.getUpperBounds();
                if (upperBounds != null) {
                    final int length = upperBounds.length;
                    if (length > 0) {
                        for (int i = 0; i < length && returnValue == null; i++) {
                            returnValue =
                                extractRelevantQualifiers(qualifiersSupplier,
                                                          upperBounds[i],
                                                          testClass,
                                                          seen); // RECURSIVE
                            if (returnValue != null) {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return returnValue;
    }

    private static boolean noBeans(final BeanManager beanManager, final Type type, final Annotation... qualifiers) {
        Objects.requireNonNull(beanManager);
        Objects.requireNonNull(type);
        final Collection<?> beans;
        if (qualifiers != null && qualifiers.length > 0) {
            beans = beanManager.getBeans(type, qualifiers);
        } else {
            beans = beanManager.getBeans(type);
        }
        return beans == null || beans.isEmpty();
    }

}
