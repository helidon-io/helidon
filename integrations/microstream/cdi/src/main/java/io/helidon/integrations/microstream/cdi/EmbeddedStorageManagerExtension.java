/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import io.helidon.config.Config;
import io.helidon.integrations.microstream.core.EmbeddedStorageManagerBuilder;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

/**
 * An {@link Extension} that arranges for named {@link MicrostreamStorage}
 * injection points to be satisfied.
 * @deprecated Microstream is renamed to Eclipse store and no longer updated
 */
@Deprecated(forRemoval = true, since = "4.2.1")
@SuppressWarnings("checkstyle:IllegalToken") // deprecated, to be removed
public class EmbeddedStorageManagerExtension implements Extension {

    private final Map<Set<Annotation>, Object> embeddedStorageBeans;

    private Config config;

    /**
     * Creates a new {@link EmbeddedStorageManagerExtension}.
     */
    public EmbeddedStorageManagerExtension() {
        super();
        embeddedStorageBeans = new HashMap<>();
    }

    private void configure(@Observes @Priority(PLATFORM_BEFORE) Config config) {
        this.config = config;
    }

    /*
     * Collect all injection points qualifiers for EmbeddedStorageManagers
     */
    private <T extends EmbeddedStorageManager> void processInjectionPoint(@Observes final ProcessInjectionPoint<?, T> event) {
        if (event != null) {
            final InjectionPoint injectionPoint = event.getInjectionPoint();
            if (injectionPoint != null) {
                this.embeddedStorageBeans.put(injectionPoint.getQualifiers(), null);
            }
        }
    }

    /*
     * create EmbeddedStorageManager beans
     */
    private void addBeans(@Observes final AfterBeanDiscovery event, final BeanManager beanManager) {
        if (event != null && beanManager != null) {
            if (!this.embeddedStorageBeans.isEmpty()) {
                for (final Entry<Set<Annotation>, ?> entry : this.embeddedStorageBeans.entrySet()) {
                    assert entry != null;
                    if (entry.getValue() == null) {
                        // create EmbeddedStorageManager bean
                        final Set<Annotation> qualifiers = entry.getKey();
                        assert qualifiers != null;
                        assert !qualifiers.isEmpty();
                        event.<EmbeddedStorageManager>addBean().scope(ApplicationScoped.class)
                                .addTransitiveTypeClosure(EmbeddedStorageManager.class)
                                .beanClass(EmbeddedStorageManager.class)
                                .qualifiers(qualifiers)
                                .createWith(cc -> EmbeddedStorageManagerBuilder.create(getConfigNode(qualifiers)).start())
                                .destroyWith((storageManager, context) -> storageManager.shutdown());
                    }
                }
            }
        }
    }

    /*
     * Get the config node that matches the name supplied by @MicrostreamStorage annotation
     * if no name is available the full helidon config is returned
     */
    private Config getConfigNode(Set<Annotation> qualifiers) {
        Optional<Annotation> optAnnotation = qualifiers.stream().filter(e -> e instanceof MicrostreamStorage)
                .findFirst();
        if (optAnnotation.isPresent()) {
            MicrostreamStorage value = (MicrostreamStorage) optAnnotation.get();
            String name = value.configNode();
            return config.get(name);
        }
        return config;
    }
}
