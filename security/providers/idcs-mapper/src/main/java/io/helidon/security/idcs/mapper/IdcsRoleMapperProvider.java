/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.security.idcs.mapper;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.oidc.common.OidcConfig;
import io.helidon.security.providers.EvictableCache;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SubjectMappingProvider;

/**
 * {@link SubjectMappingProvider} to obtain roles from IDCS server for a user.
 */
public final class IdcsRoleMapperProvider implements SubjectMappingProvider {
    private static final Logger LOGGER = Logger.getLogger(IdcsRoleMapperProvider.class.getName());
    private static final String ACCESS_TOKEN_KEY = "access_token";

    private final EvictableCache<String, List<? extends Grant>> roleCache;
    private final WebTarget assertEndpoint;
    private final WebTarget tokenEndpoint;

    // caching application token (as that can be re-used for group requests)
    private volatile SignedJwt appToken;
    private volatile Jwt appJwt;

    private IdcsRoleMapperProvider(Builder builder) {
        this.roleCache = builder.roleCache;
        OidcConfig oidcConfig = builder.oidcConfig;

        this.assertEndpoint = oidcConfig.generalClient().target(oidcConfig.identityUri() + "/admin/v1/Asserter");
        this.tokenEndpoint = oidcConfig.tokenEndpoint();
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
     * <li>oidc-config to load an instance of {@link OidcConfig}</li>
     * <li>cache-config (optional) to load an instance of {@link EvictableCache} for role caching</li>
     * </ul>
     *
     * @param config configuration of this provider
     * @return a new instance configured from config
     */
    public static SecurityProvider create(Config config) {
        return builder().fromConfig(config).build();
    }

    @Override
    public CompletionStage<AuthenticationResponse> map(ProviderRequest authenticatedRequest,
                                                       AuthenticationResponse previousResponse) {
        // this only supports users
        return previousResponse.getUser().map(subject -> enhance(subject, previousResponse))
                .orElseGet(() -> CompletableFuture.completedFuture(previousResponse));
    }

    private CompletionStage<AuthenticationResponse> enhance(Subject subject,
                                                            AuthenticationResponse previousResponse) {
        String username = subject.getPrincipal().getName();

        List<? extends Grant> grants = roleCache.computeValue(username, () -> getGrantsFromServer(username))
                .orElse(CollectionsHelper.listOf());

        AuthenticationResponse.Builder builder = AuthenticationResponse.builder();
        builder.user(buildSubject(subject, grants));
        previousResponse.getService().ifPresent(builder::service);
        previousResponse.getDescription().ifPresent(builder::description);
        builder.requestHeaders(previousResponse.getRequestHeaders());

        AuthenticationResponse response = builder.build();

        return CompletableFuture.completedFuture(response);
    }

    private Subject buildSubject(Subject originalSubject, List<? extends Grant> grants) {
        Subject.Builder builder = Subject.builder();
        builder.update(originalSubject);

        grants.forEach(builder::addGrant);

        return builder.build();
    }

    private Optional<List<? extends Grant>> getGrantsFromServer(String subject) {
        return getAppToken().flatMap(appToken -> {
            JsonObjectBuilder requestBuilder = Json.createObjectBuilder();
            requestBuilder.add("mappingAttributeValue", subject);
            requestBuilder.add("includeMemberships", true);
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            arrayBuilder.add("urn:ietf:params:scim:schemas:oracle:idcs:Asserter");
            requestBuilder.add("schemas", arrayBuilder);

            Response groupResponse = assertEndpoint
                    .request()
                    .header("Authorization", "Bearer " + appToken)
                    .post(Entity.json(requestBuilder.build()));

            if (groupResponse.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                JsonObject jsonObject = groupResponse.readEntity(JsonObject.class);
                JsonArray groups = jsonObject.getJsonArray("groups");

                List<Role> result = new LinkedList<>();

                for (int i = 0; i < groups.size(); i++) {
                    JsonObject groupJson = groups.getJsonObject(i);
                    String groupName = groupJson.getString("display");
                    String groupId = groupJson.getString("value");
                    String groupRef = groupJson.getString("$ref");

                    Role role = Role.builder()
                            .name(groupName)
                            .addAttribute("groupId", groupId)
                            .addAttribute("groupRef", groupRef)
                            .build();

                    result.add(role);
                }

                return Optional.of(result);
            } else {
                LOGGER.warning("Cannot read groups for user \""
                                       + subject
                                       + "\". Response code: "
                                       + groupResponse.getStatus()
                                       + ", entity: "
                                       + groupResponse.readEntity(String.class));
                return Optional.empty();
            }
        });
    }

    private synchronized Optional<String> getAppToken() {
        // if cached and valid, use the cached token
        return OptionalHelper.from(getCachedAppToken())
                // otherwise retrieve a new one (and cache it as a side effect)
                .or(this::getAndCacheAppTokenFromServer)
                .asOptional()
                // we are interested in the text content of the token
                .map(SignedJwt::getTokenContent);
    }

    private Optional<SignedJwt> getCachedAppToken() {
        if (null == appToken) {
            return Optional.empty();
        }

        if (appJwt.validate(Jwt.defaultTimeValidators()).isValid()) {
            return Optional.of(appToken);
        }

        appToken = null;
        appJwt = null;

        return Optional.empty();
    }

    private Optional<SignedJwt> getAndCacheAppTokenFromServer() {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.putSingle("grant_type", "client_credentials");
        formData.putSingle("scope", "urn:opc:idm:__myscopes__");

        Response tokenResponse = tokenEndpoint
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(formData));

        if (tokenResponse.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            JsonObject response = tokenResponse.readEntity(JsonObject.class);
            String accessToken = response.getString(ACCESS_TOKEN_KEY);
            LOGGER.finest(() -> "Access token: " + accessToken);
            SignedJwt signedJwt = SignedJwt.parseToken(accessToken);

            this.appToken = signedJwt;
            this.appJwt = signedJwt.getJwt();

            return Optional.of(signedJwt);
        } else {
            LOGGER.severe("Failed to obtain access token for application to read groups"
                                  + " from IDCS. Response code: " + tokenResponse.getStatus() + ", entity: "
                                  + tokenResponse.readEntity(String.class));
            return Optional.empty();
        }
    }

    /**
     * Fluent API builder for {@link IdcsRoleMapperProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<IdcsRoleMapperProvider> {
        private OidcConfig oidcConfig;
        private EvictableCache<String, List<? extends Grant>> roleCache;

        @Override
        public IdcsRoleMapperProvider build() {
            if (null == roleCache) {
                roleCache = EvictableCache.create();
            }
            return new IdcsRoleMapperProvider(this);
        }

        /**
         * Update this builder state from configuration.
         * Expects:
         * <ul>
         * <li>oidc-config to load an instance of {@link OidcConfig}</li>
         * <li>cache-config (optional) to load an instance of {@link EvictableCache} for role caching</li>
         * </ul>
         *
         * @param config current node must have "oidc-config" as one of its children
         * @return updated builder instance
         */
        public Builder fromConfig(Config config) {
            config.get("oidc-config").asOptional(OidcConfig.class).ifPresent(this::oidcConfig);
            config.get("cache-config").asOptional(EvictableCache.class).ifPresent(this::roleCache);
            return this;
        }

        /**
         * Use explicit {@link OidcConfig} instance, e.g. when using it also for OIDC provider.
         *
         * @param config oidc specific configuration, must have at least identity endpoint and client credentials configured
         * @return updated builder instance
         */
        public Builder oidcConfig(OidcConfig config) {
            this.oidcConfig = config;
            return this;
        }

        /**
         * Use explicit {@link EvictableCache} for role caching.
         *
         * @param roleCache cache to use
         * @return update builder instance
         */
        public Builder roleCache(EvictableCache<String, List<? extends Grant>> roleCache) {
            this.roleCache = roleCache;
            return this;
        }
    }
}
