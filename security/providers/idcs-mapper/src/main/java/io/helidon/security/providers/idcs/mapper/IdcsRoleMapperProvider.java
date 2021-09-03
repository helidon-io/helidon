/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Subject;
import io.helidon.security.integration.common.RoleMapTracing;
import io.helidon.security.integration.common.SecurityTracing;
import io.helidon.security.providers.common.EvictableCache;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.spi.SubjectMappingProvider;

/**
 * {@link SubjectMappingProvider} to obtain roles from IDCS server for a user.
 * Supports multi tenancy in IDCS.
 *
 * @deprecated use {@link io.helidon.security.providers.idcs.mapper.IdcsRoleMapperRxProvider} instead
 */
@Deprecated(forRemoval = true, since = "2.4.0")
public class IdcsRoleMapperProvider extends IdcsRoleMapperProviderBase implements SubjectMappingProvider {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final EvictableCache<String, List<Grant>> roleCache;
    private final WebTarget assertEndpoint;

    // caching application token (as that can be re-used for group requests)
    private final AppToken appToken;

    /**
     * Constructor that accepts any {@link io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProvider.Builder} descendant.
     *
     * @param builder used to configure this instance
     */
    protected IdcsRoleMapperProvider(Builder<?> builder) {
        super(builder);

        this.roleCache = builder.roleCache;
        OidcConfig oidcConfig = builder.oidcConfig();

        this.assertEndpoint = oidcConfig.generalClient().target(oidcConfig.identityUri() + "/admin/v1/Asserter");
        WebTarget tokenEndpoint = oidcConfig.tokenEndpoint();

        appToken = new IdcsMtRoleMapperProvider.AppToken(tokenEndpoint);
    }

    /**
     * Creates a new builder to build instances of this class.
     *
     * @return a new fluent API builder.
     */
    public static Builder<?> builder() {
        return new Builder<>();
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
        return builder().config(config).build();
    }

    @Override
    protected Subject enhance(Subject subject, ProviderRequest request, AuthenticationResponse previousResponse) {
        String username = subject.principal().getName();

        List<? extends Grant> grants = roleCache.computeValue(username, () -> computeGrants(subject))
                .orElseGet(LinkedList::new);

        List<Grant> result = addAdditionalGrants(subject)
                .map(newGrants -> {
                    List<Grant> newList = new LinkedList<>(grants);
                    newList.addAll(newGrants);
                    return newList;
                })
                .orElseGet(() -> new LinkedList<>(grants));

        return buildSubject(subject, result);
    }

    /**
     * Compute grants for the provided subject.
     * This implementation gets grants from server {@link #getGrantsFromServer(io.helidon.security.Subject)}.
     *
     * @param subject to retrieve roles (or in general {@link io.helidon.security.Grant grants})
     * @return An optional list of grants to be added to the subject
     */
    protected Optional<List<Grant>> computeGrants(Subject subject) {
        List<Grant> result = new LinkedList<>();

        getGrantsFromServer(subject)
                .map(result::addAll);

        return (result.isEmpty() ? Optional.empty() : Optional.of(result));
    }

    /**
     * Extension point to add additional grants that are not retrieved from IDCS.
     *
     * @param subject subject to enhance
     * @return grants to add to the subject
     */
    protected Optional<List<? extends Grant>> addAdditionalGrants(Subject subject) {
        return Optional.empty();
    }

    /**
     * Retrieves grants from IDCS server.
     *
     * @param subject to get grants for
     * @return optional list of grants to be added
     */
    protected Optional<List<? extends Grant>> getGrantsFromServer(Subject subject) {
        String subjectName = subject.principal().getName();
        String subjectType = (String) subject.principal().abacAttribute("sub_type").orElse(defaultIdcsSubjectType());

        RoleMapTracing tracing = SecurityTracing.get().roleMapTracing("idcs");

        return appToken.getToken(tracing).flatMap(appToken -> {
            JsonObjectBuilder requestBuilder = JSON.createObjectBuilder()
                    .add("mappingAttributeValue", subjectName)
                    .add("subjectType", subjectType)
                    .add("includeMemberships", true);

            JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
            arrayBuilder.add("urn:ietf:params:scim:schemas:oracle:idcs:Asserter");
            requestBuilder.add("schemas", arrayBuilder);

            try {
                Invocation.Builder reqBuilder = assertEndpoint.request();
                tracing.findParent()
                        .ifPresent(spanContext -> reqBuilder.property(PARENT_CONTEXT_CLIENT_PROPERTY, spanContext));

                Response groupResponse = reqBuilder
                        .header("Authorization", "Bearer " + appToken)
                        .post(Entity.json(requestBuilder.build()));

                return processServerResponse(groupResponse, subjectName);
            } catch (Exception e) {
                tracing.error(e);
                throw e;
            } finally {
                tracing.finish();
            }
        });
    }

    /**
     * Fluent API builder for {@link IdcsRoleMapperProvider}.
     *
     * @param <B> type of builder extending this builder
     */
    public static class Builder<B extends Builder<B>> extends IdcsRoleMapperProviderBase.Builder<Builder<B>>
            implements io.helidon.common.Builder<IdcsRoleMapperProvider> {
        private EvictableCache<String, List<Grant>> roleCache;

        @SuppressWarnings("unchecked")
        private B me = (B) this;

        /**
         * Default contructor.
         */
        protected Builder() {
        }

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
        public B config(Config config) {
            super.config(config);
            config.get("cache-config").as(EvictableCache::<String, List<Grant>>create).ifPresent(this::roleCache);

            return me;
        }

        /**
         * Use explicit {@link EvictableCache} for role caching.
         *
         * @param roleCache cache to use
         * @return update builder instance
         */
        public B roleCache(EvictableCache<String, List<Grant>> roleCache) {
            this.roleCache = roleCache;
            return me;
        }
    }
}
