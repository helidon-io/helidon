/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.core.Application;

import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.openapi.OpenAPISupport;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import org.eclipse.microprofile.config.Config;
import org.jboss.jandex.IndexView;

/**
 * Fluent builder for OpenAPISupport in Helidon MP.
 */
public final class MPOpenAPIBuilder extends OpenAPISupport.Builder<MPOpenAPIBuilder> {

    private static final Logger LOGGER = Logger.getLogger(MPOpenAPIBuilder.class.getName());

    private Optional<OpenApiConfig> openAPIConfig;

    /*
     * Provided by the OpenAPI CDI extension for retrieving a single IndexView of all scanned types for the single-app or
     * synthetic app cases.
     */
    private Supplier<? extends IndexView> singleIndexViewSupplier = null;

    protected MPOpenAPIBuilder() {
        super(MPOpenAPIBuilder.class);
    }

    @Override
    public OpenApiConfig openAPIConfig() {
        return openAPIConfig.get();
    }

    @Override
    public MPOpenAPISupport build() {
        MPOpenAPISupport result = new MPOpenAPISupport(this);
        validate();
        return result;
    }

    /**
     * Returns the {@code JaxRsApplication} instances that should be run, according to the JAX-RS CDI extension.
     *
     * This excludes synthetic applications that are ad hoc collections of otherwise unassociated JAX-RS resources.
     *
     * @return List of JaxRsApplication instances that should be run
     */
    static List<JaxRsApplication> jaxRsApplicationsToRun() {
        JaxRsCdiExtension ext = CDI.current()
                .getBeanManager()
                .getExtension(JaxRsCdiExtension.class);

        List<JaxRsApplication> jaxRsAppsToRun = ext.applicationsToRun();

        return jaxRsAppsToRun.stream()
                .filter(MPOpenAPIBuilder::isNonSynthetic)
                .collect(Collectors.toList());
    }

    private static boolean isNonSynthetic(JaxRsApplication jaxRsApp) {
        return !jaxRsApp.synthetic();
    }

    private List<Class<?>> classesToScanForAppInstance(Application app) {
        List<Class<?>> result = new ArrayList<>();

        result.addAll(app.getClasses());
        app.getSingletons().stream()
                .map(Object::getClass)
                .forEach(result::add);

        return result;
    }

    /**
     * Sets the OpenApiConfig instance to use in governing the behavior of the
     * smallrye OpenApi implementation.
     *
     * @param config {@link OpenApiConfig} instance to control OpenAPI behavior
     * @return updated builder instance
     */
    private MPOpenAPIBuilder openAPIConfig(OpenApiConfig config) {
        this.openAPIConfig = Optional.of(config);
        return this;
    }

    MPOpenAPIBuilder config(Config mpConfig) {
        openAPIConfig(new OpenApiConfigImpl(mpConfig));
        return this;
    }

    MPOpenAPIBuilder singleIndexViewSupplier(Supplier<? extends IndexView> singleIndexViewSupplier) {
        this.singleIndexViewSupplier = singleIndexViewSupplier;
        return this;
    }

    @Override
    protected Supplier<? extends IndexView> indexViewSupplier() {
        return singleIndexViewSupplier;
    }

    @Override
    public void validate() throws IllegalStateException {
        super.validate();
        if (!openAPIConfig.isPresent()) {
            throw new IllegalStateException("OpenApiConfig has not been set in MPBuilder");
        }
        Objects.requireNonNull(singleIndexViewSupplier, "singleIndexViewSupplier must be set but was not");
    }

}
