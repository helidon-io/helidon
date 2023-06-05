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

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import io.helidon.config.Config;
import io.helidon.microprofile.servicecommon.HelidonRestCdiExtension;
import io.helidon.openapi.OpenApiFeature;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.ProcessManagedBean;
import org.eclipse.microprofile.config.ConfigProvider;

import static jakarta.interceptor.Interceptor.Priority.PLATFORM_AFTER;

/**
 * Portable extension to allow construction of a Jandex index (to pass to
 * SmallRye OpenAPI) from CDI if no {@code META-INF/jandex.idx} file exists on
 * the class path.
 */
public class OpenApiCdiExtension extends HelidonRestCdiExtension<MpOpenApiFeature> {

    private static final System.Logger LOGGER = System.getLogger(OpenApiCdiExtension.class.getName());

    /**
     * Normal location of Jandex index files.
     */
    static final String INDEX_PATH = "META-INF/jandex.idx";


    private static Function<Config, MpOpenApiFeature> featureFactory(String... indexPaths) {
        return (Config helidonConfig) -> {

            org.eclipse.microprofile.config.Config mpConfig = ConfigProvider.getConfig();

            MPOpenAPIBuilder builder = MpOpenApiFeature.builder()
                    .config(helidonConfig)
                    .indexPaths(indexPaths)
                    .config(mpConfig);
            return builder.build();
        };
    }

    private final Set<Class<?>> annotatedTypes = new HashSet<>();

    /**
     * Creates a new instance of the index builder.
     *
     * @throws java.io.IOException in case of error checking for the Jandex index files
     */
    public OpenApiCdiExtension() throws IOException {
        this(INDEX_PATH);
    }

    OpenApiCdiExtension(String... indexPaths) throws IOException {
        super(LOGGER, featureFactory(indexPaths), OpenApiFeature.Builder.CONFIG_KEY);
    }

    @Override
    protected void processManagedBean(ProcessManagedBean<?> processManagedBean) {
        // SmallRye handles annotation processing. We have this method because the abstract superclass requires it.
    }


    // Must run after the server has created the Application instances.
    void buildModel(@Observes @Priority(PLATFORM_AFTER + 100 + 10) @Initialized(ApplicationScoped.class) Object event) {
        serviceSupport().prepareModel();
    }

    // For testing
     MpOpenApiFeature feature() {
        return serviceSupport();
    }


    Set<Class<?>> annotatedTypes() {
        return annotatedTypes;
    }

    /**
     * Records each type that is annotated.
     *
     * @param <X> annotated type
     * @param event {@code ProcessAnnotatedType} event
     */
    private <X> void processAnnotatedType(@Observes ProcessAnnotatedType<X> event) {
        Class<?> c = event.getAnnotatedType()
                .getJavaClass();
        annotatedTypes.add(c);
    }
}
