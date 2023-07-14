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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.openapi.OpenApiFeature;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.ext.Provider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

/**
 * Builder for the MP OpenAPI feature.
 */
class MPOpenAPIBuilder extends OpenApiFeature.Builder<MPOpenAPIBuilder, MpOpenApiFeature> {

    private static final System.Logger LOGGER = System.getLogger(MPOpenAPIBuilder.class.getName());

    // This is the prefix users will use in the config file.
    static final String MP_OPENAPI_CONFIG_PREFIX = "mp." + OpenApiFeature.Builder.CONFIG_KEY;

    private static final String USE_JAXRS_SEMANTICS_CONFIG_KEY = "use-jaxrs-semantics";

    private static final String USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY =
            "mp.openapi.extensions.helidon." + USE_JAXRS_SEMANTICS_CONFIG_KEY;
    private static final boolean USE_JAXRS_SEMANTICS_DEFAULT = true;

    private OpenApiConfig openApiConfig;
    private org.eclipse.microprofile.config.Config mpConfig;

    private String[] indexPaths;
    private int indexURLCount;

    private boolean useJaxRsSemantics = USE_JAXRS_SEMANTICS_DEFAULT;

    MPOpenAPIBuilder() {
        super();
    }

    @Override
    public MpOpenApiFeature build() {
        List<URL> indexURLs = findIndexFiles(indexPaths);
        indexURLCount = indexURLs.size();
        if (indexURLs.isEmpty()) {
            LOGGER.log(Level.INFO, String.format("""
                 OpenAPI feature could not locate the Jandex index file %s so will build an in-memory index.
                 This slows your app start-up and, depending on CDI configuration, might omit some type information \
                 needed for a complete OpenAPI document.
                 Consider using the Jandex maven plug-in during your build to create the index and add it to your app.""",
                                                 OpenApiCdiExtension.INDEX_PATH));
        }
        if (openApiConfig == null) {
            openApiConfig = new OpenApiConfigImpl(mpConfig);
        }
        return new MpOpenApiFeature(this);
    }

    @Override
    public MPOpenAPIBuilder config(Config config) {
        super.config(config);
        return identity();
    }

    /**
     * Sets the SmallRye OpenAPI configuration.
     *
     * @param openApiConfig the {@link io.smallrye.openapi.api.OpenApiConfig} settings
     * @return updated builder
     */
    public MPOpenAPIBuilder openApiConfig(OpenApiConfig openApiConfig) {
        this.openApiConfig = openApiConfig;
        return this;
    }

    /**
     * Returns an {@link org.jboss.jandex.IndexView} for the Jandex index that describes
     * annotated classes for endpoints.
     *
     * @return {@code IndexView} describing discovered classes
     */
    IndexView indexView() {
        try {
            return indexURLCount > 0 ? existingIndexFileReader() : indexFromHarvestedClasses();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the {@link io.smallrye.openapi.api.OpenApiConfig} instance the builder uses.
     *
     * @return {@code OpenApiConfig} instance in use by the builder
     */
    OpenApiConfig openApiConfig() {
        return openApiConfig;
    }

    @Override
    protected System.Logger logger() {
        return LOGGER;
    }

    MPOpenAPIBuilder config(org.eclipse.microprofile.config.Config mpConfig) {
        this.mpConfig = mpConfig;
        // use-jaxrs-semantics is intended for Helidon's private use in running the TCKs to work around a problem there.
        // We do not document its use.
        useJaxRsSemantics = mpConfig
                .getOptionalValue(USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY, Boolean.class)
                .orElse(USE_JAXRS_SEMANTICS_DEFAULT);

        return openApiConfig(new OpenApiConfigImpl(mpConfig));
    }

    MPOpenAPIBuilder indexPaths(String... indexPaths) {
        this.indexPaths = indexPaths;
        return identity();
    }

    /**
     * Creates a {@link io.smallrye.openapi.runtime.scanner.FilteredIndexView} tailored to the specified JAX-RS application.
     * <p>
     * Use an {@link io.smallrye.openapi.api.OpenApiConfig} instance which (possibly) limits scanning for this application
     * by excluding classes that are not "relevant" to the specified application. For our purposes, the classes "relevant"
     * to an application are those:
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
     * @param jaxRsApplications    all JAX-RS applications discovered
     * @param jaxRsApp             the specific JAX-RS application of interest
     * @param ancillaryClassNames  names of resource, provider, and feature classes
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
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, String.format(
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
        if ((
                classesFromGetClasses.isEmpty()
                        && (classesFromGetSingletons.isEmpty() || !useJaxRsSemantics))
                && jaxRsApplications.size() == 1) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, String.format("""
                        No filtering required for %s; although it returns a non-empty set from getSingletons, JAX-RS semantics \
                        has been turned off for OpenAPI processing using %s""",
                                                      appClassName, MPOpenAPIBuilder.USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY));
            }
            return viewFilteredByConfig;
        }

        Set<String> excludedClasses = classNamesToIgnore(jaxRsApplications,
                                                         jaxRsApp,
                                                         ancillaryClassNames,
                                                         classesExplicitlyReferenced);

        // Create a new filtered index view for this application which excludes the irrelevant classes we just identified. Its
        // delegate is the previously-created view based only on the MP configuration.
        FilteredIndexView result = new FilteredIndexView(viewFilteredByConfig,
                                                         new FilteringOpenApiConfigImpl(mpConfig, excludedClasses));
        if (LOGGER.isLoggable(Level.TRACE)) {
            String knownClassNames = result
                    .getKnownClasses()
                    .stream()
                    .map(ClassInfo::toString)
                    .sorted()
                    .collect(Collectors.joining("," + System.lineSeparator() + "    "));
            LOGGER.log(Level.TRACE,
                       String.format("FilteredIndexView for %n"
                                             + "  application class %s%n"
                                             + "  with explicitly-referenced classes %s%n"
                                             + "  yields exclude list: %s%n"
                                             + "  and known classes: %n  %s",
                                     appClassName,
                                     classesExplicitlyReferenced,
                                     excludedClasses,
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

    private static boolean isConcrete(ClassInfo classInfo) {
        return !Modifier.isAbstract(classInfo.flags());
    }

    private static class FilteringOpenApiConfigImpl extends OpenApiConfigImpl {

        private final Set<String> classesToExclude;

        FilteringOpenApiConfigImpl(org.eclipse.microprofile.config.Config config, Set<String> classesToExclude) {
            super(config);
            this.classesToExclude = classesToExclude;
        }

        @Override
        public Set<String> scanExcludeClasses() {
            return classesToExclude;
        }
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
    List<FilteredIndexView> buildPerAppFilteredIndexViews() {

        List<JaxRsApplication> jaxRsApplications = MpOpenApiFeature.jaxRsApplicationsToRun().stream()
                .filter(jaxRsApp -> jaxRsApp.applicationClass().isPresent())
                .sorted(Comparator.comparing(jaxRsApplication -> jaxRsApplication.applicationClass()
                        .get()
                        .getName()))
                .collect(Collectors.toList());

        IndexView indexView = indexView();

        FilteredIndexView viewFilteredByConfig = new FilteredIndexView(indexView, new OpenApiConfigImpl(mpConfig));
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
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Ancillary classes: {0}", result);
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

    /**
     * Builds an {@code IndexView} from existing Jandex index file(s) on the classpath.
     *
     * @return IndexView from all index files
     * @throws java.io.IOException in case of error attempting to open an index file
     */
    private IndexView existingIndexFileReader() throws IOException {
        List<IndexView> indices = new ArrayList<>();
        /*
         * Do not reuse the previously-computed indexURLs; those values will be incorrect with native images.
         */
        for (URL indexURL : findIndexFiles(indexPaths)) {
            try (InputStream indexIS = indexURL.openStream()) {
                LOGGER.log(Level.TRACE, "Adding Jandex index at {0}", indexURL.toString());
                indices.add(new IndexReader(indexIS).read());
            } catch (Exception ex) {
                throw new IOException("Attempted to read from previously-located index file "
                                              + indexURL + " but the index cannot be read", ex);
            }
        }
        return indices.size() == 1 ? indices.get(0) : CompositeIndex.create(indices);
    }

    private IndexView indexFromHarvestedClasses() throws IOException {
        Indexer indexer = new Indexer();
        annotatedTypes().forEach(c -> addClassToIndexer(indexer, c));

        /*
         * Some apps might be added dynamically, not via annotation processing. Add those classes to the index if they are not
         * already present.
         */
        MpOpenApiFeature.jaxRsApplicationsToRun().stream()
                .map(JaxRsApplication::applicationClass)
                .filter(Optional::isPresent)
                .forEach(appClassOpt -> addClassToIndexer(indexer, appClassOpt.get()));

        LOGGER.log(Level.TRACE, "Using internal Jandex index created from CDI bean discovery");
        Index result = indexer.complete();
        dumpIndex(Level.DEBUG, result);
        return result;
    }

    private void addClassToIndexer(Indexer indexer, Class<?> c) {
        try (InputStream is = MpOpenApiFeature.contextClassLoader().getResourceAsStream(resourceNameForClass(c))) {
            if (is != null) {
                indexer.index(is);
            }
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Cannot load bytecode from class %s at %s for annotation processing",
                                                     c.getName(), resourceNameForClass(c)), ex);
        }
    }

    private static void dumpIndex(Level level, Index index) {
        if (LOGGER.isLoggable(level)) {
            LOGGER.log(level, "Dump of internal Jandex index:");
            PrintStream oldStdout = System.out;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PrintStream newPS = new PrintStream(baos, true, Charset.defaultCharset())) {
                System.setOut(newPS);
                index.printAnnotations();
                index.printSubclasses();
                LOGGER.log(level, baos.toString(Charset.defaultCharset()));
            } finally {
                System.setOut(oldStdout);
            }
        }
    }

    private static String resourceNameForClass(Class<?> c) {
        return c.getName().replace('.', '/') + ".class";
    }

    private List<URL> findIndexFiles(String... indexPaths) {
        List<URL> result = new ArrayList<>();
        for (String indexPath : indexPaths) {
            Enumeration<URL> urls = null;
            try {
                urls = MpOpenApiFeature.contextClassLoader().getResources(indexPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            while (urls.hasMoreElements()) {
                result.add(urls.nextElement());
            }
        }
        return result;
    }

    private Set<Class<?>> annotatedTypes() {
        return CDI.current().getBeanManager().getExtension(OpenApiCdiExtension.class).annotatedTypes();
    }
}
