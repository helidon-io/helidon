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


//    /**
//     * Returns the {@code JaxRsApplication} instances that should be run, according to the JAX-RS CDI extension.
//     *
//     * @return List of JaxRsApplication instances that should be run
//     */
//    static List<JaxRsApplication> jaxRsApplicationsToRun(CDI<?> cdi) {
//        JaxRsCdiExtension ext = cdi
//                .getBeanManager()
//                .getExtension(JaxRsCdiExtension.class);
//
//        return ext.applicationsToRun();
//    }



//    private final String[] indexPaths;

    private final Set<Class<?>> annotatedTypes = new HashSet<>();

//    private org.eclipse.microprofile.config.Config mpConfig;
//    private Config config;
//    private MpOpenApiFeature openApiSupport;

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
//        this.indexPaths = indexPaths;
//        List<URL> indexURLs = findIndexFiles(indexPaths);
//        indexURLCount = indexURLs.size();
//        if (indexURLs.isEmpty()) {
//            LOGGER.log(Level.INFO, () -> String.format(
//                    "OpenAPI feature could not locate the Jandex index file %s "
//                            + "so will build an in-memory index.%n"
//                            + "This slows your app start-up and, depending on CDI configuration, "
//                            + "might omit some type information needed for a complete OpenAPI document.%n"
//                            + "Consider using the Jandex maven plug-in during your build "
//                            + "to create the index and add it to your app.",
//                    INDEX_PATH));
//        }
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


    //    private void configure(@Observes @RuntimeStart Config config) {
//        this.mpConfig = (org.eclipse.microprofile.config.Config) config;
//        this.config = config;
//    }

//    @Override
//    public HttpRules registerService(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class)
//                                     Object adv,
//                                     BeanManager bm,
//                                     ServerCdiExtension server) {
//        HttpRules defaultRouting = super.registerService(adv, bm, server);
//
//        org.eclipse.microprofile.config.Config config = ConfigProvider.getConfig();
//        if (!config.getOptionalValue("openapi.enabled", Boolean.class).orElse(true)) {
//            LOGGER.log(Level.TRACE, "Health support is disabled in configuration");
//        }
//        return defaultRouting;
//    }
//    void registerOpenApi(@Observes @Priority(LIBRARY_BEFORE + 10) @Initialized(ApplicationScoped.class) Object event) {
//        Config openapiNode = config.get(OpenApiFeature.Builder.CONFIG_KEY);
//        openApiSupport = new MPOpenAPIBuilder()
//                .config(mpConfig)
//                .singleIndexViewSupplier(this::indexView)
//                .config(openapiNode)
//                .build();
//
//        openApiSupport
//                .configureEndpoint(RoutingBuilders.create(openapiNode).routingBuilder());
//    }

//    // Must run after the server has created the Application instances.
//    void buildModel(@Observes @Priority(PLATFORM_AFTER + 100 + 10) @Initialized(ApplicationScoped.class) Object event) {
//        openApiSupport.prepareModel();
//    }

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

//    /**
//     * Reports an {@link org.jboss.jandex.IndexView} for the Jandex index that describes
//     * annotated classes for endpoints.
//     *
//     * @return {@code IndexView} describing discovered classes
//     */
//    public IndexView indexView() {
//        try {
//            return indexURLCount > 0 ? existingIndexFileReader() : indexFromHarvestedClasses();
//        } catch (IOException e) {
//            // wrap so we can use this method in a reference
//            throw new RuntimeException(e);
//        }
//    }

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
//                LOGGER.log(Level.DEBUG, "Adding Jandex index at {0}", indexURL.toString());
//                indices.add(new IndexReader(indexIS).read());
//            } catch (Exception ex) {
//                throw new IOException("Attempted to read from previously-located index file "
//                        + indexURL + " but the index cannot be read", ex);
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
//        MPOpenAPIBuilder.jaxRsApplicationsToRun().stream()
//                .map(JaxRsApplication::applicationClass)
//                .filter(Optional::isPresent)
//                .forEach(appClassOpt -> addClassToIndexer(indexer, appClassOpt.get()));
//
//        LOGGER.log(Level.DEBUG, "Using internal Jandex index created from CDI bean discovery");
//        Index result = indexer.complete();
//        dumpIndex(Level.TRACE, result);
//        return result;
//    }

//    private void addClassToIndexer(Indexer indexer, Class<?> c) {
//        try (InputStream is = contextClassLoader().getResourceAsStream(resourceNameForClass(c))) {
//            indexer.index(is);
//        } catch (IOException ex) {
//            throw new RuntimeException(String.format("Cannot load bytecode from class %s at %s for annotation processing",
//                    c.getName(), resourceNameForClass(c)), ex);
//        }
//    }
//
//    private List<URL> findIndexFiles(String... indexPaths) throws IOException {
//        List<URL> result = new ArrayList<>();
//        for (String indexPath : indexPaths) {
//            Enumeration<URL> urls = contextClassLoader().getResources(indexPath);
//            while (urls.hasMoreElements()) {
//                result.add(urls.nextElement());
//            }
//        }
//        return result;
//    }
//
//    private static void dumpIndex(Level level, Index index) {
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

//    private static ClassLoader contextClassLoader() {
//        return Thread.currentThread().getContextClassLoader();
//    }
//
//    private static String resourceNameForClass(Class<?> c) {
//        return c.getName().replace('.', '/') + ".class";
//    }
}
