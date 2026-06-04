/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

package io.helidon.openapi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.openapi.spi.OpenApiManagerProvider;
import io.helidon.openapi.spi.OpenApiServiceProvider;
import io.helidon.openapi.spi.OpenApiVersion;
import io.helidon.openapi.spi.OpenApiVersionProvider;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * OpenAPI feature configuration.
 */
@Prototype.Blueprint(decorator = OpenApiFeatureConfigSupport.BuilderDecorator.class)
@Prototype.Configured("openapi")
@Prototype.CustomMethods(OpenApiFeatureConfigSupport.class)
@Prototype.Provides(ServerFeatureProvider.class)
@Prototype.RegistrySupport
interface OpenApiFeatureConfigBlueprint extends Prototype.Factory<OpenApiFeature> {
    /**
     * Weight of the OpenAPI feature. This is quite low, to be registered after routing.
     * {@value io.helidon.openapi.OpenApiFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(OpenApiFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * Sets whether the feature should be enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    @Option.Decorator(OpenApiFeatureConfigSupport.EnabledDecorator.class)
    @Option.Configured("enabled")
    @Option.DefaultBoolean(true)
    boolean isEnabled();

    /**
     * Web context path for the OpenAPI endpoint.
     *
     * @return webContext to use
     */
    @Option.Configured
    @Option.Default("/openapi")
    String webContext();

    /**
     * Path of the static OpenAPI document file. Default types are `json`, `yaml`, and `yml`.
     *
     * @return location of the static OpenAPI document file
     */
    @Option.Configured
    Optional<String> staticFile();

    /**
     * Generated document source handling mode.
     *
     * @return generated document source handling mode
     */
    @Option.Configured("generated.mode")
    @Option.Default("STATIC_FIRST")
    OpenApiGeneratedMode generatedMode();

    /**
     * Whether generated document sources resolve annotation string values as Helidon config expressions at runtime.
     * <p>
     * When disabled, generated document sources use annotation string values literally. This is disabled by default
     * because annotation text can be user-visible OpenAPI content.
     *
     * @return whether to resolve generated OpenAPI annotation string values as config expressions
     */
    @Option.Configured("generated.resolve-config-expressions")
    @Option.DefaultBoolean(false)
    boolean generatedResolveConfigExpressions();

    /**
     * Named generated document metadata sources to use, in the order configured.
     * <p>
     * Sources generated from {@link OpenApi.Document @OpenApi.Document} use the annotated type's dotted canonical type
     * name. Custom {@link io.helidon.openapi.spi.OpenApiDocumentSource} services must be qualified with
     * {@link io.helidon.service.registry.Service.Named @Service.Named} to be selected by name. Unqualified sources always
     * participate when they support the document context and cannot be filtered with this option.
     *
     * @return generated document metadata source names
     */
    @Option.Configured("generated.document-sources")
    @Option.Singular
    List<String> generatedDocumentSources();

    /**
     * Operation ids to use for generated Java methods.
     * <p>
     * Each key is a Java method signature consisting of the fully qualified class name, {@code #},
     * the method name, and fully qualified parameter types separated by {@code ,}. The value is the
     * operation id to use for that method.
     *
     * @return generated operation ids keyed by fully qualified Java method signature
     */
    @Option.Configured("generated.operation-ids")
    Map<String, String> generatedOperationIds();

    /**
     * OpenAPI version implementation for rendered generated or merged documents.
     * <p>
     * If not configured, service discovery selects the highest-weighted available OpenAPI version provider. This means
     * that adding OpenAPI 3.1 or 3.2 support modules can change generated output to the highest available OpenAPI
     * document version. Configure this option to pin the OpenAPI document version for generated output.
     * <p>
     * Static documents served directly by {@link OpenApiGeneratedMode#STATIC_ONLY} or by a static hit in
     * {@link OpenApiGeneratedMode#STATIC_FIRST} are not re-rendered through this provider.
     *
     * @return version implementation for generated or merged OpenAPI documents
     */
    @Option.Configured("document")
    @Option.Provider(OpenApiVersionProvider.class)
    Optional<OpenApiVersion> openApiVersion();

    /**
     * OpenAPI services.
     *
     * @return the OpenAPI services
     */
    @Option.Configured
    @Option.Provider(OpenApiServiceProvider.class)
    @Option.Singular
    List<OpenApiService> services();

    /**
     * OpenAPI manager.
     *
     * @return the OpenAPI manager
     */
    @Option.Configured
    @Option.Provider(value = OpenApiManagerProvider.class, discoverServices = false)
    Optional<OpenApiManager<?>> manager();

    /**
     * Whether to allow anybody to access the endpoint.
     *
     * @return whether to permit access to metrics endpoint to anybody, defaults to {@code true}
     * @see #roles()
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean permitAll();

    /**
     * Hints for role names the user is expected to be in.
     *
     * @return list of hints
     */
    @Option.Configured
    @Option.Default("openapi")
    List<String> roles();

    /**
     * Name of this instance.
     *
     * @return instance name, used when discovered from configuration
     */
    @Option.Default(OpenApiFeature.OPENAPI_ID)
    String name();

    /**
     * List of sockets to register this feature on. If empty, it would get registered on all sockets.
     *
     * @return socket names to register on, defaults to empty (all available sockets)
     */
    @Option.Configured
    @Option.Singular
    Set<String> sockets();

    /**
     * Runtime service registry supplied to the builder.
     *
     * @return runtime service registry
     */
    @Option.Access("")
    Optional<ServiceRegistry> runtimeServiceRegistry();

    /**
     * Runtime source configuration supplied to the builder.
     *
     * @return runtime source configuration
     */
    @Option.Access("")
    Optional<Object> sourceRoot();
}
