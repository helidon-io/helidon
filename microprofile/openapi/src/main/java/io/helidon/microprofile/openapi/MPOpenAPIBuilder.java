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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private List<FilteredIndexView> filteredIndexViews = null;
    private Config mpConfig;


    @Override
    public OpenApiConfig openAPIConfig() {
        return openAPIConfig.get();
    }

    @Override
    public synchronized List<FilteredIndexView> filteredIndexViews() {
        if (filteredIndexViews == null) {
            filteredIndexViews = buildFilteredIndexViews();
        }
        return filteredIndexViews;
    }

    private List<FilteredIndexView> buildFilteredIndexViews() {
        /*
         * The JaxRsCdiExtension knows about all the apps in the system. For each app find out the classes related to that
         * app -- the application class itself and any resource classes reported by its getClasses() or getSingletons()
         * methods -- and create a FilteredIndexView that will be used to restrict scanning to only those classes for that app.
         */
        JaxRsCdiExtension ext = CDI.current()
                .getBeanManager()
                .getExtension(JaxRsCdiExtension.class);

        /*
         * Each set in the list holds the classes related to one app.
         */
        List<Set<Class<?>>> appClassesToScan = ext.applicationsToRun().stream()
                .map(JaxRsApplication::applicationClass)
                .flatMap(Optional::stream)
                .map(this::classesToScanForAppClass)
                .collect(Collectors.toList());

        if (appClassesToScan.size() <= 1) {
            /*
             * Use normal scanning with a FilteredIndexView containing no class restrictions (beyond what might already be in
             * the configuration).
             */
            return List.of(new FilteredIndexView(indexView.get(), openAPIConfig.get()));
        }
        return appClassesToScan.stream()
                .map(this::appRelatedClassesToFilteredIndexView)
                .collect(Collectors.toList());
    }

    private <T extends Application> Set<Class<?>> classesToScanForAppClass(Class<T> appClass) {
        Set<Class<?>> classesToScanForThisApp = new HashSet<>();
        Application app = instantiate(appClass);

        classesToScanForThisApp.add(appClass);
        classesToScanForThisApp.addAll(app.getClasses());
        app.getSingletons().stream()
                .map(Object::getClass)
                .forEach(classesToScanForThisApp::add);
        return classesToScanForThisApp;
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

    private static <T> T instantiate(Class<T> cl) {
        try {
            return cl.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
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
