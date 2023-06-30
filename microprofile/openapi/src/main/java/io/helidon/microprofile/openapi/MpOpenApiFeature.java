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
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.LazyValue;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.openapi.OpenApiFeature;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.util.MergeUtil;
import io.smallrye.openapi.runtime.OpenApiProcessor;
import io.smallrye.openapi.runtime.OpenApiStaticFile;
import io.smallrye.openapi.runtime.io.Format;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.jboss.jandex.IndexView;

/**
 * MP variant of OpenApiFeature.
 */
public class MpOpenApiFeature extends OpenApiFeature {

    /**
     * Creates a new builder for the MP OpenAPI feature.
     *
     * @return new builder
     */
    public static MPOpenAPIBuilder builder() {
        return new MPOpenAPIBuilder();
    }

    /**
     * Parser helper.
     */
    static final LazyValue<ParserHelper> PARSER_HELPER = LazyValue.create(ParserHelper::create);

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

    private final Supplier<List<FilteredIndexView>> filteredIndexViewsSupplier;

    private final Lock modelAccess = new ReentrantLock(true);

    private final OpenApiConfig openApiConfig;
    private final io.helidon.openapi.OpenApiStaticFile openApiStaticFile;

    private final MPOpenAPIBuilder builder;
    private OpenAPI model;

    private final Map<Class<?>, ExpandedTypeDescription> implsToTypes;

    protected MpOpenApiFeature(MPOpenAPIBuilder builder) {
        super(LOGGER, builder);
        this.builder = builder;
        implsToTypes = buildImplsToTypes();
        openApiConfig = builder.openApiConfig();
        openApiStaticFile = builder.staticFile();
        filteredIndexViewsSupplier = builder::buildPerAppFilteredIndexViews;
    }

    @Override
    protected String openApiContent(OpenAPIMediaType openApiMediaType) {

        return openApiContent(openApiMediaType, model());
    }

    /**
     * Triggers preparation of the model from external code.
     */
    protected void prepareModel() {
        model();
    }

    /**
     * Returns the current thread's context class loader.
     *
     * @return class loader in use by the thread
     */
    static ClassLoader contextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    // For testing
    IndexView indexView() {
        return builder.indexView();
    }

    Map<Class<?>, ExpandedTypeDescription> buildImplsToTypes() {
        return Collections.unmodifiableMap(PARSER_HELPER.get().types()
                                                   .values()
                                                   .stream()
                                                   .collect(Collectors.toMap(ExpandedTypeDescription::impl,
                                                                             Function.identity())));
    }


    private String openApiContent(OpenAPIMediaType openAPIMediaType, OpenAPI model) {
        StringWriter sw = new StringWriter();
        Serializer.serialize(PARSER_HELPER.get().types(), implsToTypes, model, openAPIMediaType, sw);
        return sw.toString();
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
                OpenApiDocument.INSTANCE.modelFromStaticFile(OpenApiParser.parse(PARSER_HELPER.get().types(),
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
        return access(() -> {
            if (model == null) {
                model = prepareModel(openApiConfig, toSmallRye(openApiStaticFile), filteredIndexViewsSupplier.get());
            }
            return model;
        });
    }

    private static OpenApiStaticFile toSmallRye(io.helidon.openapi.OpenApiStaticFile staticFile) {

        return staticFile == null
                ? null
                : new OpenApiStaticFile(
                        new BufferedInputStream(
                                new ByteArrayInputStream(staticFile.content()
                                                                 .getBytes(Charset.defaultCharset()))),
                        toFormat(staticFile.openApiMediaType()));
    }

    private <T> T access(Supplier<T> operation) {
        modelAccess.lock();
        try {
            return operation.get();
        } finally {
            modelAccess.unlock();
        }
    }
}
