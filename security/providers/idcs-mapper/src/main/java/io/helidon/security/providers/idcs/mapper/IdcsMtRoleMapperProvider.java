/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityException;
import io.helidon.security.Subject;
import io.helidon.security.providers.common.EvictableCache;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.util.TokenHandler;

/**
 * {@link io.helidon.security.spi.SubjectMappingProvider} to obtain roles from IDCS server for a user.
 * Supports multi tenancy in IDCS.
 */
public class IdcsMtRoleMapperProvider extends IdcsRoleMapperProviderBase {
    protected static final String IDCS_TENANT_HEADER = "X-USER-IDENTITY-SERVICE-GUID";
    protected static final String IDCS_APP_HEADER = "X-RESOURCE-SERVICE-INSTANCE-IDENTITY-APPNAME";

    private static final Logger LOGGER = Logger
            .getLogger(IdcsMtRoleMapperProvider.class.getName());
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final TokenHandler idcsTenantTokenHandler;
    private final TokenHandler idcsAppNameTokenHandler;
    private final EvictableCache<MtCacheKey, List<Grant>> cache;
    private final MultitenancyEndpoints multitenantEndpoints;
    private final ConcurrentHashMap<String, AppToken> tokenCache = new ConcurrentHashMap<>();

    protected IdcsMtRoleMapperProvider(Builder<?> builder) {
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

    protected Subject enhance(Subject subject,
                              ProviderRequest request,
                              AuthenticationResponse previousResponse) {

        Optional<String> maybeIdcsTenantId = idcsTenantTokenHandler.extractToken(request.env().headers());
        Optional<String> maybeIdcsAppName = idcsAppNameTokenHandler.extractToken(request.env().headers());

        if (!maybeIdcsAppName.isPresent() || !maybeIdcsTenantId.isPresent()) {
            LOGGER.finest(() -> "Missing multitenant information TENANT: " + maybeIdcsTenantId + ", APP_NAME: " + maybeIdcsAppName +
                    ", subject: " + subject);
            return subject;
        }

        String idcsAppName = maybeIdcsAppName.get();
        String idcsTenantId = maybeIdcsTenantId.get();
        String name = subject.principal().getName();
        MtCacheKey cacheKey = new MtCacheKey(idcsTenantId, idcsAppName, name);

        // double cache
        List<Grant> serverGrants = cache.computeValue(cacheKey, () -> computeGrants(idcsTenantId, idcsAppName, subject))
                .orElseGet(CollectionsHelper::listOf);

        List<Grant> grants = new LinkedList<>(serverGrants);

        // additional grants may not be cached (leave this decision to overriding class)
        addAdditionalGrants(idcsTenantId, idcsAppName, subject)
                .map(grants::addAll);

        return buildSubject(subject, grants);
    }

    private Optional<List<Grant>> computeGrants(String idcsTenantId, String idcsAppName, Subject subject) {
        return getGrantsFromServer(idcsTenantId, idcsAppName, subject)
                .map(grants -> Collections.unmodifiableList(new LinkedList<>(grants)));

    }

    /**
     * Extension point to add additional grants to the subject being created.
     *
     * @param idcsTenantId IDCS tenant id
     * @param idcsAppName  IDCS application name
     * @param subject      subject of the user/service
     * @return list with new grants to add to the enhanced subject
     */
    protected Optional<List<? extends Grant>> addAdditionalGrants(String idcsTenantId, String idcsAppName, Subject subject) {
        return Optional.empty();
    }

    protected Optional<List<? extends Grant>> getGrantsFromServer(String idcsTenantId, String idcsAppName, Subject subject) {
        String subjectName = subject.principal().getName();
        String subjectType = (String) subject.principal().abacAttribute("sub_type").orElse(defaultIdcsSubjectType());

        return getAppToken(idcsTenantId).flatMap(appToken -> {
            JsonObjectBuilder requestBuilder = JSON.createObjectBuilder()
                    .add("mappingAttributeValue", subjectName)
                    .add("subjectType", subjectType)
                    .add("appName", idcsAppName)
                    .add("includeMemberships", true);

            JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
            arrayBuilder.add("urn:ietf:params:scim:schemas:oracle:idcs:Asserter");
            requestBuilder.add("schemas", arrayBuilder);

            Response groupResponse = multitenantEndpoints.assertEndpoint(idcsTenantId)
                    .request()
                    .header("Authorization", "Bearer " + appToken)
                    .post(Entity.json(requestBuilder.build()));

            return processServerResponse(groupResponse, subjectName);
        });
    }

    /**
     * Gets token from cache or from server.
     *
     * @param idcsTenantId id of tenant
     * @return the token to be used to authenticate this service
     */
    protected Optional<String> getAppToken(String idcsTenantId) {
        // if cached and valid, use the cached token
        return tokenCache.computeIfAbsent(idcsTenantId, key -> new AppToken(multitenantEndpoints.tokenEndpoint(idcsTenantId)))
                .getToken();
    }

    protected MultitenancyEndpoints multitenancyEndpoints() {
        return multitenantEndpoints;
    }

    /**
     * Fluent API builder for {@link io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider}.
     */
    public static class Builder<B extends Builder<B>>
            extends IdcsRoleMapperProviderBase.Builder<Builder<B>>
            implements io.helidon.common.Builder<IdcsMtRoleMapperProvider> {
        private TokenHandler idcsAppNameTokenHandler = TokenHandler.forHeader(IDCS_APP_HEADER);
        private TokenHandler idcsTenantTokenHandler = TokenHandler.forHeader(IDCS_TENANT_HEADER);
        private MultitenancyEndpoints multitentantEndpoints;
        private EvictableCache<MtCacheKey, List<Grant>> cache;

        @SuppressWarnings("unchecked")
        private B me = (B) this;

        protected Builder() {
        }

        @Override
        public IdcsMtRoleMapperProvider build() {
            if (null == cache) {
                cache = EvictableCache.create();
            }
            return new IdcsMtRoleMapperProvider(this);
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
         * By default the header {@value IdcsMtRoleMapperProvider#IDCS_APP_HEADER} is used.
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
         * By default the header {@value IdcsMtRoleMapperProvider#IDCS_TENANT_HEADER} is used.
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
         * Asserter endpoint for a specific tenant.
         *
         * @param tenantId id of tenant to get the endpoint for
         * @return web target for the tenant
         */
        WebTarget assertEndpoint(String tenantId);

        /**
         * Token endpoint for a specific tenant.
         *
         * @param tenantId id of tenant to get the endpoint for
         * @return web target for the tenant
         */
        WebTarget tokenEndpoint(String tenantId);
    }

    /**
     * Default implementation of the
     * {@link io.helidon.security.providers.idcs.mapper.IdcsMtRoleMapperProvider.MultitenancyEndpoints}.
     * Caches the endpoints per tenant.
     */
    protected static class DefaultMultitenancyEndpoints implements MultitenancyEndpoints {
        private final String idcsInfraTenantId;
        private final String idcsInfraHostName;
        private final String urlPrefix;
        private final String assertUrlSuffix;
        private final String tokenUrlSuffix;
        private final Client appClient;
        private final Client generalClient;

        // we want to cache endpoints for each tenant
        private final ConcurrentHashMap<String, WebTarget> assertEndpointCache = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, WebTarget> tokenEndpointCache = new ConcurrentHashMap<>();

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
            this.generalClient = config.generalClient();
            this.appClient = config.appClient();
        }

        @Override
        public String idcsInfraTenantId() {
            return idcsInfraTenantId;
        }

        @Override
        public WebTarget assertEndpoint(String tenantId) {
            return assertEndpointCache.computeIfAbsent(tenantId, theKey -> {
                String url = urlPrefix
                        + idcsInfraHostName.replaceAll(idcsInfraTenantId, tenantId)
                        + assertUrlSuffix;

                LOGGER.finest(() -> "MT Asserter endpoint: " + url);

                return generalClient.target(url);
            });
        }

        @Override
        public WebTarget tokenEndpoint(String tenantId) {
            return tokenEndpointCache.computeIfAbsent(tenantId, theKey -> {
                String url = urlPrefix
                        + idcsInfraHostName.replaceAll(idcsInfraTenantId, tenantId)
                        + tokenUrlSuffix
                        + idcsInfraTenantId;
                LOGGER.finest(() -> "MT Token endpoint: " + url);

                return appClient.target(url);
            });
        }
    }

    /**
     * Cache key for multitenant environments.
     * Used when caching user grants.
     * Suitable for use in maps and sets.
     */
    public static class MtCacheKey {
        private final String idcsTenantId;
        private final String idcsAppName;
        private final String username;

        /**
         * New (immutable) cache key.
         *
         * @param idcsTenantId IDCS stenant ID
         * @param idcsAppName IDCS application name
         * @param username username
         */
        protected MtCacheKey(String idcsTenantId, String idcsAppName, String username) {
            Objects.requireNonNull(idcsTenantId, "IDCS Tenant id is mandatory");
            Objects.requireNonNull(idcsAppName, "IDCS App id is mandatory");
            Objects.requireNonNull(username, "username is mandatory");

            this.idcsTenantId = idcsTenantId;
            this.idcsAppName = idcsAppName;
            this.username = username;
        }

        protected String idcsTenantId() {
            return idcsTenantId;
        }

        protected String username() {
            return username;
        }

        protected String idcsAppName() {
            return idcsAppName;
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
            return idcsTenantId.equals(cacheKey.idcsTenantId) &&
                    idcsAppName.equals(cacheKey.idcsAppName) &&
                    username.equals(cacheKey.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(idcsTenantId, idcsAppName, username);
        }
    }
}
