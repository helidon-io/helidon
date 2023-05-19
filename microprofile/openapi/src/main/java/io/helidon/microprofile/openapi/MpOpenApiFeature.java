/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.config.Config;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.openapi.OpenApiFeature;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.util.MergeUtil;
import io.smallrye.openapi.runtime.OpenApiProcessor;
import io.smallrye.openapi.runtime.OpenApiStaticFile;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.openapi.models.Extensible;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.Reference;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.servers.ServerVariable;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertySubstitute;

/**
 * MP variant of OpenApiFeature.
 */
public class MpOpenApiFeature extends OpenApiFeature {

    /**
     * Creates a new builder for the MP OpenAPI feature.
     *
     * @return new builder
     */
    static Builder builder() {
        return new Builder();
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

    private static final System.Logger LOGGER = System.getLogger(MpOpenApiFeature.class.getName());
    // As a static we keep a reference to the logger, thereby making sure any changes we make are persistent. (JUL holds
    // only weak references to loggers internally.)
    private static final Logger SNAKE_YAML_INTROSPECTOR_LOGGER =
            Logger.getLogger(PropertySubstitute.class.getPackage().getName());

    private static final LazyValue<SnakeYAMLParserHelper<ExpandedTypeDescription>> helper = LazyValue.create(() -> {
        Config config = Config.create();
        boolean allowSnakeYamlWarnings = (config.get("openapi.parsing.warnings.enabled").asBoolean().orElse(false));
        if (SNAKE_YAML_INTROSPECTOR_LOGGER.isLoggable(Level.WARNING) && !allowSnakeYamlWarnings) {
            SNAKE_YAML_INTROSPECTOR_LOGGER.setLevel(Level.SEVERE);
        }
        SnakeYAMLParserHelper helper = SnakeYAMLParserHelper.create(ExpandedTypeDescription::create);
        adjustTypeDescriptions(helper.types());
        return helper;
    });



//    private final String[] indexPaths;
    private final List<FilteredIndexView> filteredIndexViews;
//    private final int indexURLCount;
    private final Set<Class<?>> annotatedTypes;

    private final Lock modelAccess = new ReentrantLock(true);

    private final OpenApiConfig openApiConfig;
    private final io.helidon.openapi.OpenApiStaticFile openApiStaticFile;
    private OpenAPI model = null;


    private final Map<Class<?>, ExpandedTypeDescription> implsToTypes;

    protected MpOpenApiFeature(Builder builder) {
        super(LOGGER, builder);
        implsToTypes = buildImplsToTypes(helper.get());
        openApiConfig = builder.openApiConfig;
        openApiStaticFile = builder.staticFile();


        filteredIndexViews = builder.buildPerAppFilteredIndexViews();

        //        indexPaths = builder.indexPaths;
//        List<URL> indexURLs = findIndexFiles(builder.indexPaths);
//        indexURLCount = indexURLs.size();
//        if (indexURLs.isEmpty()) {
//            LOGGER.log(System.Logger.Level.INFO, () -> String.format(
//                    "OpenAPI feature could not locate the Jandex index file %s "
//                            + "so will build an in-memory index.%n"
//                            + "This slows your app start-up and, depending on CDI configuration, "
//                            + "might omit some type information needed for a complete OpenAPI document.%n"
//                            + "Consider using the Jandex maven plug-in during your build "
//                            + "to create the index and add it to your app.",
//                    OpenApiCdiExtension.INDEX_PATH));
//        }

        OpenApiCdiExtension ext = CDI.current()
                .getBeanManager()
                .getExtension(OpenApiCdiExtension.class);
        annotatedTypes = ext.annotatedTypes();
    }

    @Override
    protected String openApiContent(OpenAPIMediaType openApiMediaType) {

        return openApiContent(openApiMediaType, model());
    }

    Map<Class<?>, ExpandedTypeDescription> buildImplsToTypes(SnakeYAMLParserHelper<ExpandedTypeDescription> helper) {
        return Collections.unmodifiableMap(helper.entrySet().stream()
                                                   .map(Map.Entry::getValue)
                                                   .collect(Collectors.toMap(ExpandedTypeDescription::impl, Function.identity())));
    }
    private String openApiContent(OpenAPIMediaType openAPIMediaType, OpenAPI model) {
        StringWriter sw = new StringWriter();
        Serializer.serialize(helper.get().types(), implsToTypes, model, openAPIMediaType, sw);
        return sw.toString();
    }

    private static void adjustTypeDescriptions(Map<Class<?>, ExpandedTypeDescription> types) {
        /*
         * We need to adjust the {@code TypeDescription} objects set up by the generated {@code SnakeYAMLParserHelper} class
         * because there are some OpenAPI-specific issues that the general-purpose helper generator cannot know about.
         */

        /*
         * In the OpenAPI document, HTTP methods are expressed in lower-case. But the associated Java methods on the PathItem
         * class use the HTTP method names in upper-case. So for each HTTP method, "add" a property to PathItem's type
         * description using the lower-case name but upper-case Java methods and exclude the upper-case property that
         * SnakeYAML's automatic analysis of the class already created.
         */
        ExpandedTypeDescription pathItemTD = types.get(PathItem.class);
        for (PathItem.HttpMethod m : PathItem.HttpMethod.values()) {
            pathItemTD.substituteProperty(m.name().toLowerCase(), Operation.class, getter(m), setter(m));
            pathItemTD.addExcludes(m.name());
        }

        /*
         * An OpenAPI document can contain a property named "enum" for Schema and ServerVariable, but the related Java methods
         * use "enumeration".
         */
        Set.<Class<?>>of(Schema.class, ServerVariable.class).forEach(c -> {
            ExpandedTypeDescription tdWithEnumeration = types.get(c);
            tdWithEnumeration.substituteProperty("enum", List.class, "getEnumeration", "setEnumeration");
            tdWithEnumeration.addPropertyParameters("enum", String.class);
            tdWithEnumeration.addExcludes("enumeration");
        });

        /*
         * SnakeYAML derives properties only from methods declared directly by each OpenAPI interface, not from methods defined
         *  on other interfaces which the original one extends. Those we have to handle explicitly.
         */
        for (ExpandedTypeDescription td : types.values()) {
            if (Extensible.class.isAssignableFrom(td.getType())) {
                td.addExtensions();
            }
            Property defaultProperty = td.defaultProperty();
            if (defaultProperty != null) {
                td.substituteProperty("default", defaultProperty.getType(), "getDefaultValue", "setDefaultValue");
                td.addExcludes("defaultValue");
            }
            if (isRef(td)) {
                td.addRef();
            }
        }
    }

    private static boolean isRef(TypeDescription td) {
        for (Class<?> c : td.getType().getInterfaces()) {
            if (c.equals(Reference.class)) {
                return true;
            }
        }
        return false;
    }

    private static String getter(PathItem.HttpMethod method) {
        return methodName("get", method);
    }

    private static String setter(PathItem.HttpMethod method) {
        return methodName("set", method);
    }

    private static String methodName(String operation, PathItem.HttpMethod method) {
        return operation + method.name();
    }


    /**
     * Prepares the OpenAPI model that later will be used to create the OpenAPI
     * document for endpoints in this application.
     *
     * @param config {@code OpenApiConfig} object describing paths, servers, etc.
     * @param staticFile the static file, if any, to be included in the resulting model
     * @param filteredIndexViews possibly empty list of FilteredIndexViews to use in harvesting definitions from the code
     * @return the OpenAPI model
     * @throws RuntimeException in case of errors reading any existing static
     * OpenAPI document
     */
    private OpenAPI prepareModel(OpenApiConfig config, OpenApiStaticFile staticFile,
                                 List<? extends IndexView> filteredIndexViews) {
        try {
            // The write lock guarding the model has already been acquired.
            OpenApiDocument.INSTANCE.reset();
            OpenApiDocument.INSTANCE.config(config);
            OpenApiDocument.INSTANCE.modelFromReader(OpenApiProcessor.modelFromReader(config, contextClassLoader()));
            if (staticFile != null) {
                OpenApiDocument.INSTANCE.modelFromStaticFile(OpenApiParser.parse(helper.get().types(),
                                                                                 staticFile.getContent()));
            }
            if (isAnnotationProcessingEnabled(config)) {
                expandModelUsingAnnotations(config, filteredIndexViews);
            } else {
                LOGGER.log(System.Logger.Level.TRACE, "OpenAPI Annotation processing is disabled");
            }
            OpenApiDocument.INSTANCE.filter(OpenApiProcessor.getFilter(config, contextClassLoader()));
            OpenApiDocument.INSTANCE.initialize();
            OpenAPIImpl instance = OpenAPIImpl.class.cast(OpenApiDocument.INSTANCE.get());

            // Create a copy, primarily to avoid problems during unit testing.
            // The SmallRye MergeUtil omits the openapi value, so we need to set it explicitly.
            return MergeUtil.merge(new OpenAPIImpl(), instance)
                    .openapi(instance.getOpenapi());
        } catch (IOException ex) {
            throw new RuntimeException("Error initializing OpenAPI information", ex);
        }
    }


    private static OpenAPIMediaType byFormat(Format format) {
        return format.equals(Format.JSON)
                ? OpenAPIMediaType.JSON
                : OpenAPIMediaType.YAML;
    }

    private static Format toFormat(OpenAPIMediaType openAPIMediaType) {
        return openAPIMediaType.equals(OpenAPIMediaType.YAML)
                ? Format.YAML
                : Format.JSON;
    }

    private boolean isAnnotationProcessingEnabled(OpenApiConfig config) {
        return !config.scanDisable();
    }

    private void expandModelUsingAnnotations(OpenApiConfig config, List<? extends IndexView> filteredIndexViews) {
        if (filteredIndexViews.isEmpty() || config.scanDisable()) {
            return;
        }

        /*
         * Conduct a SmallRye OpenAPI annotation scan for each filtered index view, merging the resulting OpenAPI models into one.
         * The AtomicReference is effectively final so we can update the actual reference from inside the lambda.
         */
        AtomicReference<OpenAPI> aggregateModelRef = new AtomicReference<>(new OpenAPIImpl()); // Start with skeletal model
        filteredIndexViews.forEach(filteredIndexView -> {
            OpenApiAnnotationScanner scanner = new OpenApiAnnotationScanner(config, filteredIndexView,
                                                                            List.of(new HelidonAnnotationScannerExtension()));
            OpenAPI modelForApp = scanner.scan();
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {

                LOGGER.log(System.Logger.Level.DEBUG, String.format("Intermediate model from filtered index view %s:%n%s",
                                                                    filteredIndexView.getKnownClasses(),
                                                                    openApiContent(OpenAPIMediaType.YAML, modelForApp)));
            }
            aggregateModelRef.set(
                    MergeUtil.merge(aggregateModelRef.get(), modelForApp)
                            .openapi(modelForApp.getOpenapi())); // SmallRye's merge skips openapi value.

        });
        OpenApiDocument.INSTANCE.modelFromAnnotations(aggregateModelRef.get());
    }
    private OpenAPI model() {
        return access(modelAccess, () -> {
            if (model == null) {
                model = prepareModel(openApiConfig, toSmallRye(openApiStaticFile), filteredIndexViews);
            }
            return model;
        });
    }

    private static OpenApiStaticFile toSmallRye(io.helidon.openapi.OpenApiStaticFile staticFile) {

        return new OpenApiStaticFile(
                new BufferedInputStream(
                        new ByteArrayInputStream(staticFile.content()
                                                         .getBytes(Charset.defaultCharset()))),
                toFormat(staticFile.openApiMediaType()));
    }

    private static <T> T access(Lock guard, Supplier<T> operation) {
        guard.lock();
        try {
            return operation.get();
        } finally {
            guard.unlock();
        }
    }

    private static ClassLoader contextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    //    private List<URL> findIndexFiles(String... indexPaths) {
//        List<URL> result = new ArrayList<>();
//        for (String indexPath : indexPaths) {
//            Enumeration<URL> urls = null;
//            try {
//                urls = contextClassLoader().getResources(indexPath);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            while (urls.hasMoreElements()) {
//                result.add(urls.nextElement());
//            }
//        }
//        return result;
//    }
//
//    private static ClassLoader contextClassLoader() {
//        return Thread.currentThread().getContextClassLoader();
//    }

//    /**
//     * Returns an {@link org.jboss.jandex.IndexView} for the Jandex index that describes
//     * annotated classes for endpoints.
//     *
//     * @return {@code IndexView} describing discovered classes
//     */
//    public IndexView indexView() throws IOException {
//        return indexURLCount > 0 ? existingIndexFileReader() : indexFromHarvestedClasses();
//    }
//
//    /**
//     * Builds an {@code IndexView} from existing Jandex index file(s) on the classpath.
//     *
//     * @return IndexView from all index files
//     * @throws IOException in case of error attempting to open an index file
//     */
//    private IndexView existingIndexFileReader() throws IOException {
//        List<IndexView> indices = new ArrayList<>();
//        /*
//         * Do not reuse the previously-computed indexURLs; those values will be incorrect with native images.
//         */
//        for (URL indexURL : findIndexFiles(indexPaths)) {
//            try (InputStream indexIS = indexURL.openStream()) {
//                LOGGER.log(System.Logger.Level.TRACE, "Adding Jandex index at {0}", indexURL.toString());
//                indices.add(new IndexReader(indexIS).read());
//            } catch (Exception ex) {
//                throw new IOException("Attempted to read from previously-located index file "
//                                              + indexURL + " but the index cannot be read", ex);
//            }
//        }
//        return indices.size() == 1 ? indices.get(0) : CompositeIndex.create(indices);
//    }
//
//    private IndexView indexFromHarvestedClasses() throws IOException {
//        Indexer indexer = new Indexer();
//        annotatedTypes.forEach(c -> addClassToIndexer(indexer, c));
//
//        /*
//         * Some apps might be added dynamically, not via annotation processing. Add those classes to the index if they are not
//         * already present.
//         */
//        jaxRsApplicationsToRun().stream()
//                .map(JaxRsApplication::applicationClass)
//                .filter(Optional::isPresent)
//                .forEach(appClassOpt -> addClassToIndexer(indexer, appClassOpt.get()));
//
//        LOGGER.log(System.Logger.Level.TRACE, "Using internal Jandex index created from CDI bean discovery");
//        Index result = indexer.complete();
//        dumpIndex(System.Logger.Level.DEBUG, result);
//        return result;
//    }
//
//    private void addClassToIndexer(Indexer indexer, Class<?> c) {
//        try (InputStream is = contextClassLoader().getResourceAsStream(resourceNameForClass(c))) {
//            indexer.index(is);
//        } catch (IOException ex) {
//            throw new RuntimeException(String.format("Cannot load bytecode from class %s at %s for annotation processing",
//                                                     c.getName(), resourceNameForClass(c)), ex);
//        }
//    }
//
//    private static void dumpIndex(System.Logger.Level level, Index index) {
//        if (LOGGER.isLoggable(level)) {
//            LOGGER.log(level, "Dump of internal Jandex index:");
//            PrintStream oldStdout = System.out;
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            try (PrintStream newPS = new PrintStream(baos, true, Charset.defaultCharset())) {
//                System.setOut(newPS);
//                index.printAnnotations();
//                index.printSubclasses();
//                LOGGER.log(level, baos.toString(Charset.defaultCharset()));
//            } finally {
//                System.setOut(oldStdout);
//            }
//        }
//    }
//
//    private static String resourceNameForClass(Class<?> c) {
//        return c.getName().replace('.', '/') + ".class";
//    }

    /**
     * Builder for the MP OpenAPI feature.
     */
    static class Builder extends OpenApiFeature.Builder<Builder, MpOpenApiFeature> {

        private static final System.Logger LOGGER = System.getLogger(Builder.class.getName());

        // This is the prefix users will use in the config file.
        static final String MP_OPENAPI_CONFIG_PREFIX = "mp." + OpenApiFeature.Builder.CONFIG_KEY;

        private static final String USE_JAXRS_SEMANTICS_CONFIG_KEY = "use-jaxrs-semantics";

        private static final String USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY =
                "mp.openapi.extensions.helidon." + USE_JAXRS_SEMANTICS_CONFIG_KEY;
        private static final boolean USE_JAXRS_SEMANTICS_DEFAULT = true;

        private OpenApiConfig openApiConfig;
        private org.eclipse.microprofile.config.Config mpConfig;

        private String[] indexPaths;
        private List<FilteredIndexView> perAppFilteredIndexViews;
        private int indexURLCount;

        private boolean useJaxRsSemantics = USE_JAXRS_SEMANTICS_DEFAULT;

        Builder() {
            super();
        }

        @Override
        public MpOpenApiFeature build() {
            List<URL> indexURLs = findIndexFiles(indexPaths);
            indexURLCount = indexURLs.size();
            if (indexURLs.isEmpty()) {
                LOGGER.log(System.Logger.Level.INFO, () -> String.format(
                        "OpenAPI feature could not locate the Jandex index file %s "
                                + "so will build an in-memory index.%n"
                                + "This slows your app start-up and, depending on CDI configuration, "
                                + "might omit some type information needed for a complete OpenAPI document.%n"
                                + "Consider using the Jandex maven plug-in during your build "
                                + "to create the index and add it to your app.",
                        OpenApiCdiExtension.INDEX_PATH));
            }
            perAppFilteredIndexViews = buildPerAppFilteredIndexViews();
            return new MpOpenApiFeature(this);
        }

        @Override
        public Builder config(Config config) {
            super.config(config);
            return identity();
        }

        /**
         * Sets the SmallRye OpenAPI configuration.
         *
         * @param openApiConfig the {@link OpenApiConfig} settings
         * @return updated builder
         */
        public Builder openApiConfig(OpenApiConfig openApiConfig) {
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

        @Override
        protected System.Logger logger() {
            return LOGGER;
        }

        Builder config(org.eclipse.microprofile.config.Config mpConfig) {
            this.mpConfig = mpConfig;
            // use-jaxrs-semantics is intended for Helidon's private use in running the TCKs to work around a problem there.
            // We do not document its use.
            useJaxRsSemantics = mpConfig
                    .getOptionalValue(USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY, Boolean.class)
                    .orElse(USE_JAXRS_SEMANTICS_DEFAULT);

            return openApiConfig(new OpenApiConfigImpl(mpConfig));
        }

        Builder indexPaths(String... indexPaths) {
            this.indexPaths = indexPaths;
            return identity();
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
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, String.format(
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
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, String.format(
                            "No filtering required for %s; although it returns a non-empty set from getSingletons, JAX-RS semantics "
                                    + "has been turned off for OpenAPI processing using " + Builder.USE_JAXRS_SEMANTICS_FULL_CONFIG_KEY,
                            appClassName));
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
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                String knownClassNames = result
                        .getKnownClasses()
                        .stream()
                        .map(ClassInfo::toString)
                        .sorted()
                        .collect(Collectors.joining("," + System.lineSeparator() + "    "));
                LOGGER.log(System.Logger.Level.TRACE,
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
                            .map(Builder::toClassName)
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
        private List<FilteredIndexView> buildPerAppFilteredIndexViews() {

            List<JaxRsApplication> jaxRsApplications = jaxRsApplicationsToRun().stream()
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
            if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Ancillary classes: {0}", result);
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
                    .anyMatch(Builder::isConcrete);
        }

        /**
         * Builds an {@code IndexView} from existing Jandex index file(s) on the classpath.
         *
         * @return IndexView from all index files
         * @throws IOException in case of error attempting to open an index file
         */
        private IndexView existingIndexFileReader() throws IOException {
            List<IndexView> indices = new ArrayList<>();
            /*
             * Do not reuse the previously-computed indexURLs; those values will be incorrect with native images.
             */
            for (URL indexURL : findIndexFiles(indexPaths)) {
                try (InputStream indexIS = indexURL.openStream()) {
                    LOGGER.log(System.Logger.Level.TRACE, "Adding Jandex index at {0}", indexURL.toString());
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
            jaxRsApplicationsToRun().stream()
                    .map(JaxRsApplication::applicationClass)
                    .filter(Optional::isPresent)
                    .forEach(appClassOpt -> addClassToIndexer(indexer, appClassOpt.get()));

            LOGGER.log(System.Logger.Level.TRACE, "Using internal Jandex index created from CDI bean discovery");
            Index result = indexer.complete();
            dumpIndex(System.Logger.Level.DEBUG, result);
            return result;
        }

        private void addClassToIndexer(Indexer indexer, Class<?> c) {
            try (InputStream is = contextClassLoader().getResourceAsStream(resourceNameForClass(c))) {
                indexer.index(is);
            } catch (IOException ex) {
                throw new RuntimeException(String.format("Cannot load bytecode from class %s at %s for annotation processing",
                                                         c.getName(), resourceNameForClass(c)), ex);
            }
        }

        private static void dumpIndex(System.Logger.Level level, Index index) {
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
                    urls = contextClassLoader().getResources(indexPath);
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
}
