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

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Set;

import io.helidon.common.LazyValue;
import io.helidon.microprofile.server.JaxRsApplication;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.openapi.OpenApiFormat;
import io.helidon.openapi.OpenApiManager;

import io.smallrye.openapi.api.OpenApiConfig;
import io.smallrye.openapi.api.OpenApiConfigImpl;
import io.smallrye.openapi.api.OpenApiDocument;
import io.smallrye.openapi.api.models.OpenAPIImpl;
import io.smallrye.openapi.api.util.MergeUtil;
import io.smallrye.openapi.runtime.OpenApiProcessor;
import io.smallrye.openapi.runtime.scanner.AnnotationScannerExtension;
import io.smallrye.openapi.runtime.scanner.FilteredIndexView;
import io.smallrye.openapi.runtime.scanner.OpenApiAnnotationScanner;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.jboss.jandex.IndexView;

/**
 * A {@link OpenApiManager} for MicroProfile.
 */
final class MpOpenApiManager implements OpenApiManager<OpenAPI> {

    private static final System.Logger LOGGER = System.getLogger(MpOpenApiManager.class.getName());
    private static final String CONFIG_EXT_PREFIX = "mp.openapi.extensions.helidon.";

    /**
     * Full config key for the {@code JAXRS_SEMANTICS} option.
     */
    static final String USE_JAXRS_SEMANTICS_KEY = CONFIG_EXT_PREFIX + "use-jaxrs-semantics";

    private final Config config;
    private final MpOpenApiManagerConfig managerConfig;
    private final OpenApiConfig openApiConfig;
    private final List<AnnotationScannerExtension> scannerExtensions = List.of(new JsonpAnnotationScannerExtension());
    private final LazyValue<List<FilteredIndexView>> filteredIndexViews = LazyValue.create(this::buildFilteredIndexViews);

    MpOpenApiManager(Config config) {
        this.config = config;
        this.managerConfig = MpOpenApiManagerConfig.builder()
                .update(builder -> config.getOptionalValue(USE_JAXRS_SEMANTICS_KEY, Boolean.class)
                        .ifPresent(builder::useJaxRsSemantics))
                .build();
        this.openApiConfig = new OpenApiConfigImpl(config);
    }

    @Override
    public String name() {
        return "manager";
    }

    @Override
    public String type() {
        return "mp";
    }

    @Override
    public OpenAPI load(String content) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        OpenApiDocument.INSTANCE.reset();
        OpenApiDocument.INSTANCE.config(openApiConfig);
        OpenApiDocument.INSTANCE.modelFromReader(OpenApiProcessor.modelFromReader(openApiConfig, contextClassLoader));
        if (!content.isBlank()) {
            OpenAPI document = OpenApiParser.parse(OpenApiHelper.types(), OpenAPI.class, new StringReader(content));
            OpenApiDocument.INSTANCE.modelFromStaticFile(document);
        }
        if (!openApiConfig.scanDisable()) {
            processAnnotations();
        } else {
            LOGGER.log(Level.TRACE, "OpenAPI Annotation processing is disabled");
        }
        OpenApiDocument.INSTANCE.filter(OpenApiProcessor.getFilter(openApiConfig, contextClassLoader));
        OpenApiDocument.INSTANCE.initialize();
        OpenAPIImpl instance = (OpenAPIImpl) OpenApiDocument.INSTANCE.get();

        // MergeUtil omits the openapi value, so we need to set it explicitly.
        return MergeUtil.merge(new OpenAPIImpl(), instance).openapi(instance.getOpenapi());
    }

    @Override
    public String format(OpenAPI model, OpenApiFormat format) {
        StringWriter sw = new StringWriter();
        OpenApiSerializer.serialize(OpenApiHelper.types(), model, format, sw);
        return sw.toString();
    }

    /**
     * Get the filtered index views.
     *
     * @return list of filter index views
     */
    List<FilteredIndexView> filteredIndexViews() {
        return filteredIndexViews.get();
    }

    private void processAnnotations() {
        List<FilteredIndexView> indexViews = filteredIndexViews();
        if (openApiConfig.scanDisable() || indexViews.isEmpty()) {
            return;
        }

        // Conduct a SmallRye OpenAPI annotation scan for each filtered index view
        // merging the resulting OpenAPI models into one.
        OpenAPI model = new OpenAPIImpl(); // Start with skeletal model
        for (IndexView indexView : indexViews) {
            OpenApiAnnotationScanner scanner = new OpenApiAnnotationScanner(openApiConfig, indexView, scannerExtensions);
            OpenAPI scanned = scanner.scan();
            if (LOGGER.isLoggable(Level.DEBUG)) {
                LOGGER.log(Level.DEBUG, String.format(
                        "Intermediate scanned from filtered index view %s:%n%s",
                        indexView.getKnownClasses(),
                        format(scanned, OpenApiFormat.YAML)));
            }
            model = MergeUtil.merge(model, scanned).openapi(scanned.getOpenapi()); // SmallRye's merge skips openapi value.
        }
        OpenApiDocument.INSTANCE.modelFromAnnotations(model);
    }

    private List<FilteredIndexView> buildFilteredIndexViews() {
        BeanManager beanManager = CDI.current().getBeanManager();
        List<JaxRsApplication> jaxRsApps = beanManager.getExtension(JaxRsCdiExtension.class).applicationsToRun();
        Set<Class<?>> annotatedTypes = beanManager.getExtension(OpenApiCdiExtension.class).annotatedTypes();
        return new FilteredIndexViewsBuilder(config,
                                             jaxRsApps,
                                             annotatedTypes,
                                             managerConfig.indexPaths(),
                                             managerConfig.useJaxRsSemantics()).buildViews();
    }
}
