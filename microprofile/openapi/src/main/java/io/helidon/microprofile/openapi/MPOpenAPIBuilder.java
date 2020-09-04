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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.Unmanaged;
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
            return defaultResultSupplier.get();
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
     *     If there is already a pre-existing {@code Application} instance for the JAX-RS application, then
     *     use it to invoke {@code getClasses} and {@code getSingletons}. Otherwise use CDI to create
     *     an unmanaged instance of the {@code Application}, then invoke those methods, then dispose of the unmanaged instance.
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
            Unmanaged<? extends Application> unmanagedApp = new Unmanaged<>(appClass);
            Unmanaged.UnmanagedInstance<? extends Application> unmanagedInstance = unmanagedApp.newInstance();
            app = unmanagedInstance.produce()
                    .inject()
                    .postConstruct()
                    .get();
            result.addAll(classesToScanForAppInstance(app));
            unmanagedInstance.preDestroy()
                    .dispose();
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
