/*
 * Copyright (c) 2019,2020 Oracle and/or its affiliates.
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

import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
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
public final class MPOpenAPIBuilder extends OpenAPISupport.Builder {

    private Optional<OpenApiConfig> openAPIConfig;
    private Optional<IndexView> indexView;
    private List<FilteredIndexView> perAppFilteredIndexViews = null;
    private Config mpConfig;

    @Override
    public OpenApiConfig openAPIConfig() {
        return openAPIConfig.get();
    }

    @Override
    public synchronized List<FilteredIndexView> perAppFilteredIndexViews() {
        if (perAppFilteredIndexViews == null) {
            perAppFilteredIndexViews = buildPerAppFilteredIndexViews();
        }
        return perAppFilteredIndexViews;
    }

    /**
     * Returns those {@code Application} instances that are runnable, according to the server which accepts
     * dynamically-registered apps as well as ones discovered using annotations.
     *
     * @return List of Application instances that are runnable
     */
    static List<Application> appInstancesToRun() {
        JaxRsCdiExtension ext = CDI.current()
                .getBeanManager()
                .getExtension(JaxRsCdiExtension.class);

        List<JaxRsApplication> appsToRun = ext.applicationsToRun();

        return appsToRun.stream()
                .filter(MPOpenAPIBuilder::isNonSynthetic)
                .map(MPOpenAPIBuilder::appInstance)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private List<FilteredIndexView> buildPerAppFilteredIndexViews() {
        /*
         * There are two cases that return a default filtered index view. Don't create it yet, just declare a supplier for it.
         */
        Supplier<List<FilteredIndexView>> defaultResultSupplier = () -> List.of(new FilteredIndexView(indexView.get(),
                openAPIConfig.get()));

        /*
         * Some JaxRsApplication instances might have an application instance already associated with them. Others might not in
         * which case we'll try to instantiate them ourselves (unless they are synthetic apps or lack no-args constructors).
         *
         * Each set in the list holds the classes related to one app.
         *
         * Sort the stream by the Application class name to help keep the list of endpoints in the OpenAPI document in a stable
         * order.
         */
        List<Set<Class<?>>> appClassesToScan = appInstancesToRun().stream()
                .sorted(Comparator.comparing(app -> app.getClass().getName()))
                .map(this::classesToScanForApp)
                .collect(Collectors.toList());

        if (appClassesToScan.size() <= 1) {
            /*
             * Use normal scanning with a FilteredIndexView containing no class restrictions (beyond what might already be in
             * the configuration).
             */
            return defaultResultSupplier.get();
        }
        return appClassesToScan.stream()
                .map(this::appRelatedClassesToFilteredIndexView)
                .collect(Collectors.toList());
    }

    private static boolean isNonSynthetic(JaxRsApplication jaxRsApp) {
        return !jaxRsApp.synthetic();
    }

    private static Optional<? extends Application> appInstance(JaxRsApplication jaxRsApp) {
        Application preexistingApp = jaxRsApp.resourceConfig().getApplication();
        return preexistingApp != null ? Optional.of(preexistingApp)
                : jaxRsApp.applicationClass()
                        .flatMap(MPOpenAPIBuilder::instantiate);
    }

    private <T extends Application> Set<Class<?>> classesToScanForApp(Application app) {
        Set<Class<?>> result = new HashSet<>();
        result.add(app.getClass());
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

        FilteredIndexView result = new FilteredIndexView(indexView.get(), openAPIFilteringConfig);
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

    private static Optional<? extends Application> instantiate(Class<? extends Application> appClass) {
        try {
            return Optional.of(appClass.getConstructor().newInstance());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            // Wrapper app does not have a no-args constructor so we canont instantiate it.
            return Optional.empty();
        }
    }

    /**
     * Sets the IndexView instance to be passed to the smallrye OpenApi impl for
     * annotation analysis.
     *
     * @param indexView {@link IndexView} instance containing endpoint classes
     * @return updated builder instance
     */
    public MPOpenAPIBuilder indexView(IndexView indexView) {
        this.indexView = Optional.of(indexView);
        return this;
    }

    @Override
    public void validate() throws IllegalStateException {
        if (!openAPIConfig.isPresent()) {
            throw new IllegalStateException("OpenApiConfig has not been set in MPBuilder");
        }
    }

}
