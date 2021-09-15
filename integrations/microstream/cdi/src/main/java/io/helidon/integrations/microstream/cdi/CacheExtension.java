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

package io.helidon.integrations.microstream.cdi;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.ProcessInjectionPoint;

import io.helidon.common.GenericType;
import io.helidon.config.Config;
import io.helidon.integrations.microstream.cache.CacheBuilder;

import one.microstream.cache.types.Cache;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * An {@link Extension} that arranges for named {@link MicrostreamCache}
 * injection points to be satisfied.
 */
public class CacheExtension implements Extension {

    private final Set<Descriptor> cacheBeans;

    private Config config;

    /**
     * Creates a new {@link CacheExtension}.
     */
    public CacheExtension() {
        super();
        cacheBeans = new HashSet<>();
    }

    private void configure(@Observes @Priority(PLATFORM_BEFORE) Config config) {
        this.config = config;
    }

    /*
     * Collect all injection points qualifiers for Microstream Cache
     */
    private <T extends javax.cache.Cache<?, ?>> void processInjectionPoint(
            @Observes final ProcessInjectionPoint<?, T> event) {
        if (event != null) {
            final InjectionPoint injectionPoint = event.getInjectionPoint();
            if (injectionPoint != null) {
                if (injectionPoint.getAnnotated().isAnnotationPresent(MicrostreamCache.class)) {
                    this.cacheBeans.add(
                            new Descriptor(injectionPoint.getQualifiers(), (ParameterizedType) injectionPoint.getType()));
                }
            }
        }
    }

    /*
     * create EmbeddedStorageManager beans
     */
    private void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
        if (event != null && beanManager != null) {
            if (!this.cacheBeans.isEmpty()) {
                for (final Descriptor entry : this.cacheBeans) {
                    assert entry != null;
                    // create Microstream Cache bean
                    final Set<Annotation> qualifiers = entry.getAnnotations();
                    assert qualifiers != null;
                    assert !qualifiers.isEmpty();

                    ParameterizedType types = entry.getTypes();
                    GenericType<?> keyType = GenericType.create(types.getActualTypeArguments()[0]);
                    GenericType<?> valueType = GenericType.create(types.getActualTypeArguments()[1]);
                    String name = getName(qualifiers);

                    event.<Cache<?, ?>>addBean()
                            .qualifiers(qualifiers)
                            .scope(ApplicationScoped.class)
                            .addTransitiveTypeClosure(Cache.class)
                            .addTypes(types)
                            .createWith(cc -> {
                                return CacheBuilder.create(name, getConfigNode(qualifiers), keyType.rawType(),
                                                           valueType.rawType());
                            })
                            .destroyWith((cache, context) -> cache.close());
                }
            }
        }
    }

    /*
     * Get the config node that matches the name supplied by @MicrostreamStorage
     * annotation if no name is available the full helidon config is returned
     */
    private Config getConfigNode(Set<Annotation> qualifiers) {
        Optional<Annotation> optAnnotation = qualifiers.stream().filter(e -> e instanceof MicrostreamCache).findFirst();
        if (optAnnotation.isPresent()) {
            MicrostreamCache annotation = (MicrostreamCache) optAnnotation.get();
            String name = annotation.configNode();
            return config.get(name);
        }
        return null;
    }

    /*
     * Get the name supplied by @MicrostreamStorage
     * annotation if no name is available null is returned
     */
    private String getName(Set<Annotation> qualifiers) {
        Optional<Annotation> optAnnotation = qualifiers.stream().filter(e -> e instanceof MicrostreamCache).findFirst();
        if (optAnnotation.isPresent()) {
            MicrostreamCache annotation = (MicrostreamCache) optAnnotation.get();
            return annotation.name();
        }
        return null;
    }

    private static class Descriptor {

        private final Set<Annotation> annotations;
        private final ParameterizedType types;

        private Descriptor(Set<Annotation> cacheBeans, ParameterizedType types) {
            super();
            this.annotations = cacheBeans;
            this.types = types;
        }

        public Set<Annotation> getAnnotations() {
            return annotations;
        }

        public ParameterizedType getTypes() {
            return types;
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotations, types);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Descriptor other = (Descriptor) obj;
            return Objects.equals(annotations, other.annotations) && Objects.equals(types, other.types);
        }
    }
}
