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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.core.Application;

import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.openapi.OpenAPISupport;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
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

    private Config mpConfig;

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

    private List<FilteredIndexView> buildPerAppFilteredIndexViews() {
        /*
         * Some JaxRsApplication instances might have an application instance already associated with them. Others might not in
         * which case we'll try to instantiate them ourselves (unless they are synthetic apps or lack no-args constructors).
         *
         * Each set in the list holds the classes related to one app.
         *
         * Sort the stream by the Application class name to help keep the list of endpoints in the OpenAPI document in a stable
         * order.
         */
        List<Set<Class<?>>> appClassesToScan = jaxRsApplicationsToRun().stream()
                .filter(jaxRsApplication -> jaxRsApplication.applicationClass().isPresent())
                .sorted(Comparator.comparing(jaxRsApplication -> jaxRsApplication.applicationClass()
                        .get()
                        .getName()))
                .map(this::classesToScanForJaxRsApp)
                .collect(Collectors.toList());

        if (appClassesToScan.size() <= 1) {
            /*
             * Use normal scanning with a FilteredIndexView containing no class restrictions (beyond what might already be in
             * the configuration).
             */
            return List.of(new FilteredIndexView(singleIndexViewSupplier.get(), openAPIConfig.get()));
        }
        return appClassesToScan.stream()
                .map(this::appRelatedClassesToFilteredIndexView)
                .collect(Collectors.toList());
    }

    private static boolean isNonSynthetic(JaxRsApplication jaxRsApp) {
        return !jaxRsApp.synthetic();
    }

    /**
     * Returns the classes that should be scanned for the given JAX-RS application.
     * <p>
     *     This should always run after the server has instantiated the {@code Application}
     *     instance for each JAX-RS application, so we just
     *     use it to invoke {@code getClasses} and {@code getSingletons}.
     * </p>
     * @param jaxRsApplication
     * @return Set of classes to be scanned for annotations related to OpenAPI
     */
    private Set<Class<?>> classesToScanForJaxRsApp(JaxRsApplication jaxRsApplication) {
        if (jaxRsApplication.synthetic()) {
            return Collections.emptySet();
        }
        Set<Class<?>> result = new HashSet<>();
        Class<? extends Application> appClass = jaxRsApplication.applicationClass()
                .get(); // known to be present because of how this method is invoked
        result.add(appClass);
        Application app = jaxRsApplication.resourceConfig().getApplication();

        if (app != null) {
            result.addAll(classesToScanForAppInstance(app));
        } else {
            LOGGER.log(Level.WARNING, String.format("Expected application instance not created yet for %s",
                    appClass.getName()));
        }
        return result;
    }

    private List<Class<?>> classesToScanForAppInstance(Application app) {
        List<Class<?>> result = new ArrayList<>();

        result.addAll(app.getClasses());
        app.getSingletons().stream()
                .map(Object::getClass)
                .forEach(result::add);

        return result;
    }

    private FilteredIndexView appRelatedClassesToFilteredIndexView(Set<Class<?>> appRelatedClassesToScan) {
        /*
         * Create an OpenAPIConfig instance to limit scanning to this app's classes by overriding any inclusions of classes or
         * packages specified in the config with our own inclusions based on this app's classes.
         */
        OpenApiConfigImpl openAPIFilteringConfig = new OpenApiConfigImpl(mpConfig);
        Set<String> scanClasses = openAPIFilteringConfig.scanClasses();
        scanClasses.clear();
        openAPIFilteringConfig.scanPackages().clear();

        appRelatedClassesToScan.stream()
                .map(Class::getName)
                .forEach(scanClasses::add);

        FilteredIndexView result = new FilteredIndexView(singleIndexViewSupplier.get(), openAPIFilteringConfig);
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
        this.mpConfig = mpConfig;
        openAPIConfig(new OpenApiConfigImpl(mpConfig));
        return this;
    }

    MPOpenAPIBuilder singleIndexViewSupplier(Supplier<? extends IndexView> singleIndexViewSupplier) {
        this.singleIndexViewSupplier = singleIndexViewSupplier;
        return this;
    }

    @Override
    protected Supplier<List<? extends IndexView>> indexViewsSupplier() {
        return () -> buildPerAppFilteredIndexViews();
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
