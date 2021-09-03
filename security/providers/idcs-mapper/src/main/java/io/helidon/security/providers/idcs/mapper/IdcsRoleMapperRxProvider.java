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
import java.util.Optional;

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
import io.helidon.security.spi.SubjectMappingProvider;
import io.helidon.webclient.WebClientRequestBuilder;

/**
 * {@link io.helidon.security.spi.SubjectMappingProvider} to obtain roles from IDCS server for a user.
 * Supports multi tenancy in IDCS.
 */
public class IdcsRoleMapperRxProvider extends IdcsRoleMapperRxProviderBase implements SubjectMappingProvider {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    private final EvictableCache<String, List<Grant>> roleCache;
    private final String asserterUri;
    private final URI tokenEndpointUri;

    // caching application token (as that can be re-used for group requests)
    private final AppTokenRx appToken;

    /**
     * Constructor that accepts any {@link IdcsRoleMapperRxProvider.Builder} descendant.
     *
     * @param builder used to configure this instance
     */
    protected IdcsRoleMapperRxProvider(Builder<?> builder) {
        super(builder);

        this.roleCache = builder.roleCache;
        OidcConfig oidcConfig = builder.oidcConfig();

        this.asserterUri = oidcConfig.identityUri() + "/admin/v1/Asserter";
        this.tokenEndpointUri = oidcConfig.tokenEndpointUri();

        this.appToken = new AppTokenRx(oidcConfig.appWebClient(), tokenEndpointUri);
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

    @Override
    protected Single<Subject> enhance(ProviderRequest request,
                                      AuthenticationResponse previousResponse,
                                      Subject subject) {
        String username = subject.principal().getName();

        Optional<List<Grant>> grants = roleCache.computeValue(username, Optional::empty);
        if (grants.isPresent()) {
            return addAdditionalGrants(subject, grants.get())
                    .map(it -> {
                        List<Grant> allGrants = new LinkedList<>(grants.get());
                        allGrants.addAll(it);
                        return buildSubject(subject, allGrants);
                    });
        }
        // we do not have a cached value, we must request it from remote server
        // this may trigger multiple times in parallel - rather than creating a map of future for each user
        // we leave this be (as the map of futures may be unlimited)
        List<Grant> result = new LinkedList<>();
        return computeGrants(subject)
                .map(it -> {
                    result.addAll(it);
                    return result;
                })
                .map(newGrants -> roleCache.computeValue(username, () -> Optional.of(List.copyOf(newGrants)))
                        .orElseGet(List::of))
                // additional grants may not be cached (leave this decision to overriding class)
                .flatMapSingle(it -> addAdditionalGrants(subject, it))
                .map(newGrants -> {
                    result.addAll(newGrants);
                    return result;
                })
                .map(it -> buildSubject(subject, it));
    }

    /**
     * Compute grants for the provided subject.
     * This implementation gets grants from server {@link #getGrantsFromServer(io.helidon.security.Subject)}.
     *
     * @param subject to retrieve roles (or in general {@link io.helidon.security.Grant grants})
     * @return future with grants to be added to the subject
     */
    protected Single<List<? extends Grant>> computeGrants(Subject subject) {
        return getGrantsFromServer(subject);
    }

    /**
     * Extension point to add additional grants that are not retrieved from IDCS.
     *
     * @param subject subject to enhance
     * @param idcsGrants grants obtained from IDCS
     * @return grants to add to the subject
     */
    protected Single<List<? extends Grant>> addAdditionalGrants(Subject subject,
                                                                List<Grant> idcsGrants) {
        return Single.just(List.of());
    }

    /**
     * Retrieves grants from IDCS server.
     *
     * @param subject to get grants for
     * @return optional list of grants to be added
     */
    protected Single<List<? extends Grant>> getGrantsFromServer(Subject subject) {
        String subjectName = subject.principal().getName();
        String subjectType = (String) subject.principal().abacAttribute("sub_type").orElse(defaultIdcsSubjectType());

        RoleMapTracing tracing = SecurityTracing.get().roleMapTracing("idcs");

        return Single.create(appToken.getToken(tracing))
                .flatMapSingle(maybeAppToken -> {
                    if (maybeAppToken.isEmpty()) {
                        return Single.error(new SecurityException("Application token not available"));
                    }
                    String appToken = maybeAppToken.get();
                    JsonObjectBuilder requestBuilder = JSON.createObjectBuilder()
                            .add("mappingAttributeValue", subjectName)
                            .add("subjectType", subjectType)
                            .add("includeMemberships", true);

                    JsonArrayBuilder arrayBuilder = JSON.createArrayBuilder();
                    arrayBuilder.add("urn:ietf:params:scim:schemas:oracle:idcs:Asserter");
                    requestBuilder.add("schemas", arrayBuilder);

                    // use current span context as a parent for client outbound
                    // using a custom child context, so we do not replace the parent in the current context
                    Context parentContext = Contexts.context().orElseGet(Contexts::globalContext);
                    Context childContext = Context.builder()
                            .parent(parentContext)
                            .build();

                    tracing.findParent()
                            .ifPresent(childContext::register);

                    WebClientRequestBuilder request = oidcConfig().generalWebClient()
                            .post()
                            .uri(asserterUri)
                            .context(childContext)
                            .headers(it -> {
                                it.add(Http.Header.AUTHORIZATION, "Bearer " + appToken);
                                return it;
                            });

                    return processRoleRequest(request,
                                              requestBuilder.build(),
                                              subjectName);
                })
                .peek(ignored -> tracing.finish())
                .onError(tracing::error);
    }

    /**
     * Fluent API builder for {@link IdcsRoleMapperRxProvider}.
     *
     * @param <B> type of builder extending this builder
     */
    public static class Builder<B extends Builder<B>> extends IdcsRoleMapperRxProviderBase.Builder<Builder<B>>
            implements io.helidon.common.Builder<IdcsRoleMapperRxProvider> {
        private EvictableCache<String, List<Grant>> roleCache;

        @SuppressWarnings("unchecked")
        private B me = (B) this;

        /**
         * Default contructor.
         */
        protected Builder() {
        }

        @Override
        public IdcsRoleMapperRxProvider build() {
            if (null == roleCache) {
                roleCache = EvictableCache.create();
            }
            return new IdcsRoleMapperRxProvider(this);
        }

        /**
         * Update this builder state from configuration.
         * Expects:
         * <ul>
         * <li>oidc-config to load an instance of {@link io.helidon.security.providers.oidc.common.OidcConfig}</li>
         * <li>cache-config (optional) to load an instance of {@link io.helidon.security.providers.common.EvictableCache} for
         * role caching</li>
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
         * Use explicit {@link io.helidon.security.providers.common.EvictableCache} for role caching.
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
