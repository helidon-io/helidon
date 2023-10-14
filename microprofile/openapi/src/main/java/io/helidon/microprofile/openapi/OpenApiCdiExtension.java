/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.openapi;

import java.util.HashSet;
import java.util.Set;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.servicecommon.HelidonRestCdiExtension;
import io.helidon.openapi.OpenApiFeature;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import org.eclipse.microprofile.config.ConfigProvider;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Portable extension to allow construction of a Jandex index (to pass to
 * SmallRye OpenAPI) from CDI if no {@code META-INF/jandex.idx} file exists on
 * the class path.
 */
public class OpenApiCdiExtension extends HelidonRestCdiExtension {

    private static final System.Logger LOGGER = System.getLogger(OpenApiCdiExtension.class.getName());

    private final Set<Class<?>> annotatedTypes = new HashSet<>();
    private volatile OpenApiFeature feature;

    /**
     * Creates a new instance.
     */
    public OpenApiCdiExtension() {
        super(LOGGER, "openapi", "mp.openapi");
    }

    /**
     * Register the Health observer with server observer feature.
     * This is a CDI observer method invoked by CDI machinery.
     *
     * @param event  event object
     * @param server Server CDI extension
     */
    public void registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
                                Object event,
                                ServerCdiExtension server) {

        feature = OpenApiFeature.builder()
                .config(componentConfig())
                .manager(new MpOpenApiManager(ConfigProvider.getConfig()))
                .build();
        server.addFeature(feature);
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> processManagedBean) {
        // SmallRye handles annotation processing. We have this method because the abstract superclass requires it.
    }

    /**
     * Get the annotated types.
     *
     * @return annotated types
     */
    Set<Class<?>> annotatedTypes() {
        return annotatedTypes;
    }

    // Must run after the server has created the Application instances.
    private void buildModel(@Observes @Priority(PLATFORM_AFTER + 100 + 10) @Initialized(ApplicationScoped.class) Object event) {
        feature.initialize();
    }

    // Records each type that is annotated
    private <X> void processAnnotatedType(@Observes ProcessAnnotatedType<X> event) {
        annotatedTypes.add(event.getAnnotatedType().getJavaClass());
    }
}
