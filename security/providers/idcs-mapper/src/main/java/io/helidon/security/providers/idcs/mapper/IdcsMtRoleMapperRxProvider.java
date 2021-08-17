/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.security.providers.idcs.mapper;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityException;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.RoleMapTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.providers.common.EvictableCache;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.util.TokenHandler;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;

/**
 * {@link io.helidon.security.spi.SubjectMappingProvider} to obtain roles from IDCS server for a user.
 * Supports multi tenancy in IDCS.
 */
public class IdcsMtRoleMapperRxProvider extends IdcsRoleMapperRxProviderBase {
    /**
     * Name of the header containing the IDCS tenant. This is the default used, can be overridden
     * in builder by {@link IdcsMtRoleMapperRxProvider.Builder#idcsTenantTokenHandler(io.helidon.security.util.TokenHandler)}
     */
    protected static final String IDCS_TENANT_HEADER = "X-USER-IDENTITY-SERVICE-GUID";
    /**
     * Name of the header containing the IDCS app. This is the default used, can be overriden
     * in builder by {@link IdcsMtRoleMapperRxProvider.Builder#idcsAppNameTokenHandler(io.helidon.security.util.TokenHandler)}
     */
    protected static final String IDCS_APP_HEADER = "X-RESOURCE-SERVICE-INSTANCE-IDENTITY-APPNAME";

    private static final Logger LOGGER = Logger
            .getLogger(IdcsMtRoleMapperRxProvider.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final TokenHandler idcsTenantTokenHandler;
    private final TokenHandler idcsAppNameTokenHandler;
    private final EvictableCache<MtCacheKey, List<Grant>> cache;
    private final MultitenancyEndpoints multitenantEndpoints;
    private final ConcurrentHashMap<String, AppTokenRx> tokenCache = new ConcurrentHashMap<>();

    /**
     * Configure instance from any descendant of
     * {@link IdcsMtRoleMapperRxProvider.Builder}.
     *
     * @param builder containing the required configuration
     */
    protected IdcsMtRoleMapperRxProvider(Builder<?> builder) {
        super(builder);

        this.idcsTenantTokenHandler = builder.idcsTenantTokenHandler;
        this.idcsAppNameTokenHandler = builder.idcsAppNameTokenHandler;
        this.cache = builder.cache;
        if (null == builder.multitentantEndpoints) {
            this.multitenantEndpoints = new DefaultMultitenancyEndpoints(builder.oidcConfig());
        } else {
            this.multitenantEndpoints = builder.multitentantEndpoints;
        }
    }

    /**
     * Creates a new builder to build instances of this class.
     *
     * @return a new fluent API builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an instance from configuration.
     * <p>
     * Expects:
     * <ul>
     * <li>oidc-config to load an instance of {@link io.helidon.security.providers.oidc.common.OidcConfig}</li>
     * <li>cache-config (optional) to load an instance of {@link io.helidon.security.providers.common.EvictableCache} for role
     * caching</li>
     * </ul>
     *
     * @param config configuration of this provider
     * @return a new instance configured from config
     */
    public static SecurityProvider create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Enhance the subject with appropriate roles from IDCS.
     *
     * @param request          provider request
     * @param previousResponse authenticated response (never null)
     * @param subject          subject of the user (never null)
     * @return future with enhanced subject
     */
    @Override
    protected CompletionStage<Subject> enhance(ProviderRequest request,
                                               AuthenticationResponse previousResponse,
                                               Subject subject) {

        Optional<IdcsMtContext> maybeIdcsMtContext = extractIdcsMtContext(subject, request);

        if (maybeIdcsMtContext.isEmpty()) {
            LOGGER.finest(() -> "Missing multitenant information IDCS CONTEXT: "
                    + maybeIdcsMtContext
                    + ", subject: "
                    + subject);
            return CompletableFuture.completedStage(subject);
        }

        IdcsMtContext idcsMtContext = maybeIdcsMtContext.get();
        String name = subject.principal().getName();
        MtCacheKey cacheKey = new MtCacheKey(idcsMtContext, name);

        // double cache
        Optional<List<Grant>> grants = cache.computeValue(cacheKey, Optional::empty);
        if (grants.isPresent()) {
            return addAdditionalGrants(idcsMtContext.tenantId(),
                                       idcsMtContext.appId(),
                                       subject,
                                       grants.get())
                    .thenApply(it -> {
                        List<Grant> allGrants = new LinkedList<>(grants.get());
                        allGrants.addAll(it);
                        return buildSubject(subject, allGrants);
                    });
        }
        // we do not have a cached value, we must request it from remote server
        // this may trigger multiple times in parallel - rather than creating a map of future for each user
        // we leave this be (as the map of futures may be unlimited)
        List<Grant> result = new LinkedList<>();

        return computeGrants(idcsMtContext.tenantId(), idcsMtContext.appId(), subject)
                .thenApply(it -> {
                    result.addAll(it);
                    return result;
                })
                .thenApply(newGrants -> cache.computeValue(cacheKey, () -> Optional.of(List.copyOf(newGrants)))
                        .orElseGet(List::of))
                // additional grants may not be cached (leave this decision to overriding class)
                .thenCompose(it -> addAdditionalGrants(idcsMtContext.tenantId(), idcsMtContext.appId(), subject, it))
                .thenApply(newGrants -> {
                    result.addAll(newGrants);
                    return result;
                })
                .thenApply(it -> buildSubject(subject, it));
    }

    /**
     * Compute grants for the provided MT information.
     *
     * @param idcsTenantId tenant id
     * @param idcsAppName app name
     * @param subject subject
     * @return future with grants to be added to the subject
     */
    protected CompletionStage<List<? extends Grant>> computeGrants(String idcsTenantId, String idcsAppName, Subject subject) {
        return getGrantsFromServer(idcsTenantId, idcsAppName, subject);
    }

    /**
     * Extract IDCS multitenancy context form the the request.
     *
     * <p>By default, the context is extracted from the headers using token handlers for
     * {@link IdcsMtRoleMapperRxProvider.Builder#idcsTenantTokenHandler(io.helidon.security.util.TokenHandler) tenant} and
     * {@link IdcsMtRoleMapperRxProvider.Builder#idcsAppNameTokenHandler(io.helidon.security.util.TokenHandler) app}.
     * @param subject Subject that is being mapped
     * @param request ProviderRequest context that is being mapped.
     * @return Optional with the context, empty if the context is not present in the request.
     */
    protected Optional<IdcsMtContext> extractIdcsMtContext(Subject subject, ProviderRequest request) {
        return idcsTenantTokenHandler.extractToken(request.env().headers())
                .flatMap(tenant -> idcsAppNameTokenHandler.extractToken(request.env().headers())
                        .map(app -> new IdcsMtContext(tenant, app)));
    }

    /**
     * Extension point to add additional grants to the subject being created.
     *
     * @param idcsTenantId IDCS tenant id
     * @param idcsAppName  IDCS application name
     * @param subject      subject of the user/service
     * @param idcsGrants   Roles already retrieved from IDCS
     * @return list with new grants to add to the enhanced subject
     */
    protected CompletionStage<List<? extends Grant>> addAdditionalGrants(String idcsTenantId,
                                                                         String idcsAppName,
                                                                         Subject subject,
                                                                         List<Grant> idcsGrants) {
        return CompletableFuture.completedStage(List.of());
    }

    /**
     * Get grants from IDCS server. The result is cached.
     *
     * @param idcsTenantId ID of the IDCS tenant
     * @param idcsAppName  Name of IDCS application
     * @param subject      subject to get grants for
     * @return optional list of grants from server
     */
    protected CompletionStage<List<? extends Grant>> getGrantsFromServer(String idcsTenantId,
                                                                         String idcsAppName,
                                                                         Subject subject) {
        String subjectName = subject.principal().getName();
        String subjectType = (String) subject.principal().abacAttribute("sub_type").orElse(defaultIdcsSubjectType());

        RoleMapTracing tracing = SecurityTracing.get().roleMapTracing("idcs");

        return Single.create(getAppToken(idcsTenantId, tracing))
                .flatMapSingle(maybeAppToken -> {
                    if (maybeAppToken.isEmpty()) {
                        return Single.error(new SecurityException("Application token not available"));
                    }
                    return Single.just(maybeAppToken.get());
                })
                .flatMapSingle(appToken -> {
                    JsonObjectBuilder requestBuilder = JSON.createObjectBuilder()
                            .add("mappingAttributeValue", subjectName)
                            .add("subjectType", subjectType)
                            .add("appName", idcsAppName)
                            .add("includeMemberships", true);

                    JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
                    arrayBuilder.add("urn:ietf:params:scim:schemas:oracle:idcs:Asserter");
                    requestBuilder.add("schemas", arrayBuilder);

                    Context parentContext = Contexts.context().orElseGet(Contexts::globalContext);
                    Context childContext = Context.builder()
                            .parent(parentContext)
                            .build();

                    tracing.findParent()
                            .ifPresent(childContext::register);

                    WebClientRequestBuilder post = oidcConfig().generalWebClient()
                            .post()
                            .context(childContext)
                            .uri(multitenantEndpoints.assertEndpoint(idcsTenantId))
                            .headers(it -> {
                                it.add(Http.Header.AUTHORIZATION, "Bearer " + appToken);
                                return it;
                            });

                    return processRoleRequest(post, requestBuilder.build(), subjectName);
                });
    }

    /**
     * Gets token from cache or from server.
     *
     * @param idcsTenantId id of tenant
     * @param tracing Role mapping tracing instance to correctly trace outbound calls
     * @return the token to be used to authenticate this service
     */
    protected CompletionStage<Optional<String>> getAppToken(String idcsTenantId, RoleMapTracing tracing) {
        // if cached and valid, use the cached token
        return tokenCache.computeIfAbsent(idcsTenantId, key -> new AppTokenRx(oidcConfig().appWebClient(),
                                                                              multitenantEndpoints.tokenEndpoint(idcsTenantId)))
                .getToken(tracing);
    }

    /**
     * Get the {@link IdcsMtRoleMapperRxProvider.MultitenancyEndpoints} used
     * to get assertion and token endpoints of a multitenant IDCS.
     *
     * @return endpoints to use by this implementation
     */
    protected MultitenancyEndpoints multitenancyEndpoints() {
        return multitenantEndpoints;
    }

    /**
     * Multitenant endpoints for accessing IDCS services.
     */
    public interface MultitenancyEndpoints {
        /**
         * The tenant id of the infrastructure tenant.
         *
         * @return id of the tenant
         */
        String idcsInfraTenantId();

        /**
         * Asserter endpoint URI for a specific tenant.
         *
         * @param tenantId id of tenant to get the endpoint for
         * @return URI for the tenant
         */
        URI assertEndpoint(String tenantId);

        /**
         * Token endpoint URI for a specific tenant.
         *
         * @param tenantId id of tenant to get the endpoint for
         * @return URI for the tenant
         */
        URI tokenEndpoint(String tenantId);
    }

    /**
     * Fluent API builder for {@link IdcsMtRoleMapperRxProvider}.
     *
     * @param <B> type of a descendant of this builder
     */
    public static class Builder<B extends Builder<B>>
            extends IdcsRoleMapperRxProviderBase.Builder<Builder<B>>
            implements io.helidon.common.Builder<IdcsMtRoleMapperRxProvider> {
        private TokenHandler idcsAppNameTokenHandler = TokenHandler.forHeader(IDCS_APP_HEADER);
        private TokenHandler idcsTenantTokenHandler = TokenHandler.forHeader(IDCS_TENANT_HEADER);
        private MultitenancyEndpoints multitentantEndpoints;
        private EvictableCache<MtCacheKey, List<Grant>> cache;

        @SuppressWarnings("unchecked")
        private B me = (B) this;

        /**
         * Default constructor.
         */
        protected Builder() {
        }

        @Override
        public IdcsMtRoleMapperRxProvider build() {
            if (null == cache) {
                cache = EvictableCache.create();
            }
            return new IdcsMtRoleMapperRxProvider(this);
        }

        @Override
        public B config(Config config) {
            super.config(config);

            config.get("cache-config").as(EvictableCache::<MtCacheKey, List<Grant>>create).ifPresent(this::cache);
            config.get("idcs-tenant-handler").as(TokenHandler::create).ifPresent(this::idcsTenantTokenHandler);
            config.get("idcs-app-name-handler").as(TokenHandler::create).ifPresent(this::idcsAppNameTokenHandler);

            return me;
        }

        /**
         * Configure token handler for IDCS Application name.
         * By default the header {@value IdcsMtRoleMapperRxProvider#IDCS_APP_HEADER} is used.
         *
         * @param idcsAppNameTokenHandler new token handler to extract IDCS application name
         * @return updated builder instance
         */
        public B idcsAppNameTokenHandler(TokenHandler idcsAppNameTokenHandler) {
            this.idcsAppNameTokenHandler = idcsAppNameTokenHandler;
            return me;
        }

        /**
         * Configure token handler for IDCS Tenant ID.
         * By default the header {@value IdcsMtRoleMapperRxProvider#IDCS_TENANT_HEADER} is used.
         *
         * @param idcsTenantTokenHandler new token handler to extract IDCS tenant ID
         * @return updated builder instance
         */

        public B idcsTenantTokenHandler(TokenHandler idcsTenantTokenHandler) {
            this.idcsTenantTokenHandler = idcsTenantTokenHandler;
            return me;
        }

        /**
         * Replace default endpoint provider in multitenant IDCS setup.
         *
         * @param endpoints endpoints to retrieve tenant specific token and asserter endpoints
         * @return updated builder instance
         */
        public B multitenantEndpoints(MultitenancyEndpoints endpoints) {
            this.multitentantEndpoints = endpoints;
            return me;
        }

        /**
         * Use explicit {@link io.helidon.security.providers.common.EvictableCache} for role caching.
         *
         * @param roleCache cache to use
         * @return updated builder instance
         */
        public B cache(EvictableCache<MtCacheKey, List<Grant>> roleCache) {
            this.cache = roleCache;
            return me;
        }
    }

    /**
     * Default implementation of the
     * {@link IdcsMtRoleMapperRxProvider.MultitenancyEndpoints}.
     * Caches the endpoints per tenant.
     */
    protected static class DefaultMultitenancyEndpoints implements MultitenancyEndpoints {
        private final String idcsInfraTenantId;
        private final String idcsInfraHostName;
        private final String urlPrefix;
        private final String assertUrlSuffix;
        private final String tokenUrlSuffix;
        private final WebClient appClient;
        private final WebClient generalClient;

        // we want to cache endpoints for each tenant
        private final ConcurrentHashMap<String, URI> assertEndpointCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, URI> tokenEndpointCache = new ConcurrentHashMap<>();

        /**
         * Creates endpoints from provided OIDC configuration using default URIs.
         * <p>
         * <ul>
         * <li>For Asserter endpoint: {@code /admin/v1/Asserter}</li>
         * <li>For Token endpoint: {@code /oauth2/v1/token?IDCS_CLIENT_TENANT=}</li>
         * </ul>
         *
         * @param config IDCS base configuration
         */
        protected DefaultMultitenancyEndpoints(OidcConfig config) {
            idcsInfraHostName = config.identityUri().getHost();
            int index = idcsInfraHostName.indexOf('.');

            if (index == -1) {
                throw new SecurityException("Configuration of multitenant IDCS is invalid. The identity host name should be "
                                                    + "'tenant-id.identityServer' but is " + idcsInfraHostName);
            }

            idcsInfraTenantId = idcsInfraHostName.substring(0, index);
            urlPrefix = config.identityUri().getScheme() + "://";
            this.assertUrlSuffix = "/admin/v1/Asserter";
            this.tokenUrlSuffix = "/oauth2/v1/token?IDCS_CLIENT_TENANT=";
            this.generalClient = config.generalWebClient();
            this.appClient = config.appWebClient();
        }

        @Override
        public String idcsInfraTenantId() {
            return idcsInfraTenantId;
        }

        @Override
        public URI assertEndpoint(String tenantId) {
            return assertEndpointCache.computeIfAbsent(tenantId, theKey -> {
                String url = urlPrefix
                        + idcsInfraHostName.replaceAll(idcsInfraTenantId, tenantId)
                        + assertUrlSuffix;

                LOGGER.finest(() -> "MT Asserter endpoint: " + url);

                return URI.create(url);
            });
        }

        @Override
        public URI tokenEndpoint(String tenantId) {
            return tokenEndpointCache.computeIfAbsent(tenantId, theKey -> {
                String url = urlPrefix
                        + idcsInfraHostName.replaceAll(idcsInfraTenantId, tenantId)
                        + tokenUrlSuffix
                        + idcsInfraTenantId;
                LOGGER.finest(() -> "MT Token endpoint: " + url);

                return URI.create(url);
            });
        }
    }

    /**
     * Cache key for multitenant environments.
     * Used when caching user grants.
     * Suitable for use in maps and sets.
     */
    public static class MtCacheKey {
        private final IdcsMtContext idcsMtContext;
        private final String username;

        /**
         * New (immutable) cache key.
         *
         * @param idcsTenantId IDCS tenant ID
         * @param idcsAppName  IDCS application name
         * @param username     username
         */
        protected MtCacheKey(String idcsTenantId, String idcsAppName, String username) {
            this(new IdcsMtContext(
                         Objects.requireNonNull(idcsTenantId, "IDCS Tenant id is mandatory"),
                         Objects.requireNonNull(idcsAppName, "IDCS App id is mandatory")),
                 username);
        }

        /**
         * New (immutable) cache key.
         *
         * @param idcsMtContext IDCS multitenancy context
         * @param username     username
         */
        protected MtCacheKey(IdcsMtContext idcsMtContext, String username) {
            Objects.requireNonNull(idcsMtContext, "IDCS Multitenancy Context is mandatory");
            Objects.requireNonNull(username, "username is mandatory");

            this.idcsMtContext = idcsMtContext;
            this.username = username;
        }

        /**
         * IDCS Tenant ID.
         *
         * @return tenant id of the cache record
         */
        public String idcsTenantId() {
            return idcsMtContext.tenantId();
        }

        /**
         * Username.
         *
         * @return username of the cache record
         */
        public String username() {
            return username;
        }

        /**
         * IDCS Application ID.
         *
         * @return application id of the cache record
         */
        public String idcsAppName() {
            return idcsMtContext.appId();
        }

        /**
         * IDCS Multitenancy context.
         *
         * @return IDCS multitenancy context of the cache record
         */
        public IdcsMtContext idcsMtContext() {
            return idcsMtContext;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MtCacheKey)) {
                return false;
            }
            MtCacheKey cacheKey = (MtCacheKey) o;
            return idcsMtContext.equals(cacheKey.idcsMtContext)
                    && username.equals(cacheKey.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idcsMtContext, username);
        }
    }
}
