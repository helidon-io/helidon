/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.openapi.OpenAPISupport;
import io.helidon.openapi.SEOpenAPISupportBuilder;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.Config;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Fluent builder for OpenAPISupport in Helidon MP.
 */
@Configured(prefix = MPOpenAPIBuilder.MP_OPENAPI_CONFIG_PREFIX)
public final class MPOpenAPIBuilder extends OpenAPISupport.Builder<MPOpenAPIBuilder> {

    // This is the prefix users will use in the config file.
    static final String MP_OPENAPI_CONFIG_PREFIX = "mp." + SEOpenAPISupportBuilder.CONFIG_KEY;

    private static final String USE_JAXRS_SEMANTICS_CONFIG_KEY = "use-jaxrs-semantics";

    private static final String USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY =
            "mp.openapi.extensions.helidon." + USE_JAXRS_SEMANTICS_CONFIG_KEY;
    private static final boolean USE_JAXRS_SEMANTICS_DEFAULT = true;

    private static final Logger LOGGER = Logger.getLogger(MPOpenAPIBuilder.class.getName());

    private OpenApiConfig openAPIConfig;

    private boolean useJaxRsSemantics = USE_JAXRS_SEMANTICS_DEFAULT;

    /*
     * Provided by the OpenAPI CDI extension for retrieving a single IndexView of all scanned types for the single-app or
     * synthetic app cases.
     */
    private Supplier<? extends IndexView> singleIndexViewSupplier = null;

    private Config mpConfig;

    @Override
    public OpenApiConfig openAPIConfig() {
        return openAPIConfig;
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
     * @return List of JaxRsApplication instances that should be run
     */
    static List<JaxRsApplication> jaxRsApplicationsToRun() {
        JaxRsCdiExtension ext = CDI.current()
                .getBeanManager()
                .getExtension(JaxRsCdiExtension.class);

        return ext.applicationsToRun();
    }

    /**
     * Builds a list of filtered index views, one for each JAX-RS application, sorted by the Application class name to help
     * keep the list of endpoints in the OpenAPI document in a stable order.
     * <p>
     * First, we find all resource, provider, and feature classes present in the index. This is the same for all
     * applications.
     * </p>
     * <p>
     * Each filtered index view is tuned to one JAX-RS application.
     *
     * @return list of {@code FilteredIndexView}s, one per JAX-RS application
     */
    private List<FilteredIndexView> buildPerAppFilteredIndexViews() {

        List<JaxRsApplication> jaxRsApplications = jaxRsApplicationsToRun().stream()
                .filter(jaxRsApp -> jaxRsApp.applicationClass().isPresent())
                .sorted(Comparator.comparing(jaxRsApplication -> jaxRsApplication.applicationClass()
                        .get()
                        .getName()))
                .collect(Collectors.toList());

        IndexView indexView = singleIndexViewSupplier.get();

        FilteredIndexView viewFilteredByConfig = new FilteredIndexView(indexView, OpenApiConfigImpl.fromConfig(mpConfig));
        Set<String> ancillaryClassNames = ancillaryClassNames(viewFilteredByConfig);

        /*
         * Filter even for a single-application class in case it implements getClasses or getSingletons.
         */
        return jaxRsApplications.stream()
                .map(jaxRsApp -> filteredIndexView(viewFilteredByConfig,
                                                   jaxRsApplications,
                                                   jaxRsApp,
                                                   ancillaryClassNames))
                .collect(Collectors.toList());
    }

    private static Set<String> ancillaryClassNames(IndexView indexView) {
        Set<String> result = new HashSet<>(resourceClassNames(indexView));
        result.addAll(providerClassNames(indexView));
        result.addAll(featureClassNames(indexView));
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "Ancillary classes: {0}", result);
        }
        return result;
    }

    private static Set<String> resourceClassNames(IndexView indexView) {
        return annotatedClassNames(indexView, Path.class);
    }

    private static Set<String> providerClassNames(IndexView indexView) {
        return annotatedClassNames(indexView, Provider.class);
    }

    private static Set<String> featureClassNames(IndexView indexView) {
        return annotatedClassNames(indexView, Feature.class);
    }

    private static Set<String> annotatedClassNames(IndexView indexView, Class<?> annotationClass) {
        // Partially inspired by the SmallRye code.
        return indexView
                .getAnnotations(DotName.createSimple(annotationClass.getName()))
                .stream()
                .map(AnnotationInstance::target)
                .filter(target -> target.kind() == AnnotationTarget.Kind.CLASS)
                .map(AnnotationTarget::asClass)
                .filter(classInfo -> hasImplementationOrIsIncluded(indexView, classInfo))
                .map(ClassInfo::toString)
                .collect(Collectors.toSet());
    }

    private static boolean hasImplementationOrIsIncluded(IndexView indexView, ClassInfo classInfo) {
        // Partially inspired by the SmallRye code.
        return !Modifier.isInterface(classInfo.flags())
                || indexView.getAllKnownImplementors(classInfo.name()).stream()
                       .anyMatch(MPOpenAPIBuilder::isConcrete);
    }

    private static boolean isConcrete(ClassInfo classInfo) {
        return !Modifier.isAbstract(classInfo.flags());
    }

    /**
     * Creates a {@link io.smallrye.openapi.runtime.scanner.FilteredIndexView} tailored to the specified JAX-RS application.
     * <p>
     *     Use an {@link io.smallrye.openapi.api.OpenApiConfig} instance which (possibly) limits scanning for this application
     *     by excluding classes that are not "relevant" to the specified application. For our purposes, the classes "relevant"
     *     to an application are those:
     * <ul>
     *     <li>returned by the application's {@code getClasses} method, and</li>
     *     <li>inferred from the objects returned from the application's {@code getSingletons} method.</li>
     * </ul>
     *
     * If both methods return empty sets (the default implementation in {@link jakarta.ws.rs.core.Application}), then all
     * resources, providers, and features are considered relevant to the application.
     * <p>
     * In constructing the filtered index view for a JAX-RS application, we also exclude the other JAX-RS application classes.
     * </p>
     *
     * @param viewFilteredByConfig filtered index view based only on MP config
     * @param jaxRsApplications all JAX-RS applications discovered
     * @param jaxRsApp the specific JAX-RS application of interest
     * @param ancillaryClassNames names of resource, provider, and feature classes
     * @return the filtered index view suitable for the specified JAX-RS application
     */
    private FilteredIndexView filteredIndexView(FilteredIndexView viewFilteredByConfig,
                                                List<JaxRsApplication> jaxRsApplications,
                                                JaxRsApplication jaxRsApp,
                                                Set<String> ancillaryClassNames) {
        Application app = jaxRsApp.resourceConfig().getApplication();

        Set<String> classesFromGetSingletons = app.getSingletons().stream()
                .map(Object::getClass)
                .map(Class::getName)
                .collect(Collectors.toSet());

        Set<String> classesFromGetClasses = app.getClasses().stream()
                .map(Class::getName)
                .collect(Collectors.toSet());

        String appClassName = toClassName(jaxRsApp);

        Set<String> classesExplicitlyReferenced = new HashSet<>(classesFromGetClasses);
        classesExplicitlyReferenced.addAll(classesFromGetSingletons);

        if (classesExplicitlyReferenced.isEmpty() && jaxRsApplications.size() == 1) {
            // No need to do filtering at all.
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, String.format(
                        "No filtering required for %s which reports no explicitly referenced classes and "
                                + "is the only JAX-RS application",
                        appClassName));
            }
            return viewFilteredByConfig;
        }

        // Also, perform no further filtering if there is exactly one application and we found no classes from getClasses and,
        // although we found classes from getSingletons, the useJaxRsSemantics setting has been turned off.
        //
        // Note that the MP OpenAPI TCK does not follow JAX-RS behavior if the application class returns a non-empty set from
        // getSingletons; in that case, the TCK incorrectly expects the endpoints defined by other resources as well to appear
        // in the OpenAPI document.
        if ((classesFromGetClasses.isEmpty()
                     && (classesFromGetSingletons.isEmpty() || !useJaxRsSemantics))
                && jaxRsApplications.size() == 1) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, String.format(
                        "No filtering required for %s; although it returns a non-empty set from getSingletons, JAX-RS semantics "
                                + "has been turned off for OpenAPI processing using " + USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY,
                        appClassName));
            }
            return viewFilteredByConfig;
        }

        /*
         * If the classes to be ignored are A and B, the exclusion regex expression we want for filtering is
         *
         * ^(A|B)$
         *
         * The ^ and $ avoid incorrect prefix/suffix matches.
         */
        Pattern excludePattern = Pattern.compile(
                classNamesToIgnore(jaxRsApplications,
                                   jaxRsApp,
                                   ancillaryClassNames,
                                   classesExplicitlyReferenced)
                        .stream()
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|", "^(", ")$")));

        // Create a new filtered index view for this application which excludes the irrelevant classes we just identified. Its
        // delegate is the previously-created view based only on the MP configuration.
        FilteredIndexView result = new FilteredIndexView(viewFilteredByConfig,
                                                         new FilteringOpenApiConfigImpl(mpConfig, excludePattern));
        if (LOGGER.isLoggable(Level.FINE)) {
            String knownClassNames = result
                    .getKnownClasses()
                    .stream()
                    .map(ClassInfo::toString)
                    .sorted()
                    .collect(Collectors.joining("," + System.lineSeparator() + "    "));
            LOGGER.log(Level.FINE,
                       String.format("FilteredIndexView for %n"
                                             + "  application class %s%n"
                                             + "  with explicitly-referenced classes %s%n"
                                             + "  yields exclude pattern: %s%n"
                                             + "  and known classes: %n  %s",
                                     appClassName,
                                     classesExplicitlyReferenced,
                                     excludePattern,
                                     knownClassNames));
        }

        return result;
    }

    private static String toClassName(JaxRsApplication jaxRsApplication) {
        return jaxRsApplication.applicationClass()
                .map(Class::getName)
                .orElse("<unknown>");
    }

    private static Set<String> classNamesToIgnore(List<JaxRsApplication> jaxRsApplications,
                                                  JaxRsApplication jaxRsApp,
                                                  Set<String> ancillaryClassNames,
                                                  Set<String> classesExplicitlyReferenced) {

        String appClassName = toClassName(jaxRsApp);

        Set<String> result = // Start with all other JAX-RS app names.
                jaxRsApplications.stream()
                        .map(MPOpenAPIBuilder::toClassName)
                        .filter(candidateName -> !candidateName.equals("<unknown>") && !candidateName.equals(appClassName))
                        .collect(Collectors.toSet());

        if (!classesExplicitlyReferenced.isEmpty()) {
            // This class identified resource, provider, or feature classes it uses. Ignore all ancillary classes that this app
            // does not explicitly reference.
            result.addAll(ancillaryClassNames);
            result.removeAll(classesExplicitlyReferenced);
        }

        return result;
    }

    private static class FilteringOpenApiConfigImpl extends OpenApiConfigImpl {

        private final Pattern classesToExclude;

        FilteringOpenApiConfigImpl(Config config, Pattern classesToExclude) {
            super(config);
            this.classesToExclude = classesToExclude;
        }

        @Override
        public Pattern scanExcludeClasses() {
            return classesToExclude;
        }
    }

    /**
     * Sets the OpenApiConfig instance to use in governing the behavior of the
     * smallrye OpenApi implementation.
     *
     * @param config {@link OpenApiConfig} instance to control OpenAPI behavior
     * @return updated builder instance
     */
    private MPOpenAPIBuilder openAPIConfig(OpenApiConfig config) {
        this.openAPIConfig = config;
        return this;
    }

    /**
     * Assigns various OpenAPI settings from the specified MP OpenAPI {@code Config} object.
     *
     * @param mpConfig the OpenAPI {@code Config} object possibly containing settings
     * @return updated builder instance
     */
    @ConfiguredOption(type = OpenApiConfig.class, mergeWithParent = true)
    @ConfiguredOption(key = "scan.disable",
                      type = Boolean.class,
                      value = "false",
                      description = "Disable annotation scanning.")
    @ConfiguredOption(key = "scan.packages",
                      type = String.class,
                      kind = ConfiguredOption.Kind.LIST,
                      description = "Specify the list of packages to scan.")
    @ConfiguredOption(key = "scan.classes",
                      type = String.class,
                      kind = ConfiguredOption.Kind.LIST,
                      description = "Specify the list of classes to scan.")
    @ConfiguredOption(key = "scan.exclude.packages",
                      type = String.class,
                      kind = ConfiguredOption.Kind.LIST,
                      description = "Specify the list of packages to exclude from scans.")
    @ConfiguredOption(key = "scan.exclude.classes",
                      type = String.class,
                      kind = ConfiguredOption.Kind.LIST,
                      description = "Specify the list of classes to exclude from scans.")
    public MPOpenAPIBuilder config(Config mpConfig) {
        this.mpConfig = mpConfig;

        // use-jaxrs-semantics is intended for Helidon's private use in running the TCKs to work around a problem there.
        // We do not document its use.
        useJaxRsSemantics = mpConfig
                .getOptionalValue(USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY, Boolean.class)
                .orElse(USE_JAXRS_SEMANTICS_DEFAULT);
        return openAPIConfig(new OpenApiConfigImpl(mpConfig));
    }

    MPOpenAPIBuilder singleIndexViewSupplier(Supplier<? extends IndexView> singleIndexViewSupplier) {
        this.singleIndexViewSupplier = singleIndexViewSupplier;
        return this;
    }

    @Override
    protected Supplier<List<? extends IndexView>> indexViewsSupplier() {
        return this::buildPerAppFilteredIndexViews;
    }

    @Override
    public void validate() throws IllegalStateException {
        super.validate();
        if (openAPIConfig == null) {
            throw new IllegalStateException("OpenApiConfig has not been set in MPBuilder");
        }
        Objects.requireNonNull(singleIndexViewSupplier, "singleIndexViewSupplier must be set but was not");
    }

}
