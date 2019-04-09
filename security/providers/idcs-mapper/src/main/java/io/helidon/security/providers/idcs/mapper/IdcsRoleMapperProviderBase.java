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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.Grant;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.jwt.Jwt;
import io.helidon.security.jwt.SignedJwt;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.spi.SubjectMappingProvider;

/**
 * Common functionality for IDCS role mapping.
 */
public abstract class IdcsRoleMapperProviderBase implements SubjectMappingProvider {
    /**
     * User subject type used when requesting roles from IDCS.
     * An attempt is made to obtain it from JWT claim {@code sub_type}. If not defined,
     * default is used as configured in {@link io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderBase.Builder}.
     */
    public static final String IDCS_SUBJECT_TYPE_USER = "user";
    /**
     * Client subject type used when requesting roles from IDCS.
     * An attempt is made to obtain it from JWT claim {@code sub_type}. If not defined,
     * default is used as configured in {@link io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderBase.Builder}.
     */
    public static final String IDCS_SUBJECT_TYPE_CLIENT = "client";

    private static final Logger LOGGER = Logger.getLogger(IdcsRoleMapperProviderBase.class.getName());

    protected static final String ROLE_GROUP = "groups";
    protected static final String ROLE_APPROLE = "appRoles";
    protected static final String ACCESS_TOKEN_KEY = "access_token";

    private final Set<SubjectType> supportedTypes = EnumSet.noneOf(SubjectType.class);
    private final OidcConfig oidcConfig;
    private final String defaultIdcsSubjectType;

    protected IdcsRoleMapperProviderBase(Builder<?> builder) {
        this.oidcConfig = builder.oidcConfig;
        this.defaultIdcsSubjectType = builder.defaultIdcsSubjectType;
        if (builder.supportedTypes.isEmpty()) {
            this.supportedTypes.add(SubjectType.USER);
        } else {
            this.supportedTypes.addAll(builder.supportedTypes);
        }
    }

    @Override
    public CompletionStage<AuthenticationResponse> map(ProviderRequest authenticatedRequest,
                                                       AuthenticationResponse previousResponse) {

        Optional<Subject> maybeUser = previousResponse.user();
        Optional<Subject> maybeService = previousResponse.service();

        if (!maybeService.isPresent() && !maybeUser.isPresent()) {
            return complete(previousResponse);
        }

        // create a new response
        AuthenticationResponse.Builder builder = AuthenticationResponse.builder();

        maybeUser
                .map(subject -> {
                    if (supportedTypes.contains(SubjectType.USER)) {
                        return enhance(subject, authenticatedRequest, previousResponse);
                    } else {
                        return subject;
                    }
                })
                .ifPresent(builder::user);

        maybeService
                .map(subject -> {
                    if (supportedTypes.contains(SubjectType.SERVICE)) {
                        return enhance(subject, authenticatedRequest, previousResponse);
                    } else {
                        return subject;
                    }
                })
                .ifPresent(builder::service);

        previousResponse.description().ifPresent(builder::description);
        builder.requestHeaders(previousResponse.requestHeaders());

        return complete(builder.build());
    }

    protected CompletionStage<AuthenticationResponse> complete(AuthenticationResponse response) {
        return CompletableFuture.completedFuture(response);
    }

    /**
     * Enhance subject with IDCS roles.
     *
     * @param subject          subject of the user (never null)
     * @param request          provider request
     * @param previousResponse authenticated response (never null)
     * @return stage with the new authentication response
     */
    protected abstract Subject enhance(Subject subject, ProviderRequest request, AuthenticationResponse previousResponse);

    /**
     * Updates original subject with the list of grants.
     *
     * @param originalSubject as was created by authentication provider
     * @param grants          grants added by this role mapper
     * @return new subject
     */
    protected Subject buildSubject(Subject originalSubject, List<? extends Grant> grants) {
        Subject.Builder builder = Subject.builder();
        builder.update(originalSubject);

        grants.forEach(builder::addGrant);

        return builder.build();
    }

    protected Optional<List<? extends Grant>> processServerResponse(Response groupResponse, String subjectName) {
        if (groupResponse.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            JsonObject jsonObject = groupResponse.readEntity(JsonObject.class);
            JsonArray groups = jsonObject.getJsonArray("groups");
            JsonArray appRoles = jsonObject.getJsonArray("appRoles");

            if ((null == groups) && (null == appRoles)) {
                LOGGER.finest(() -> "Neither groups nor app roles found for user " + subjectName);
                return Optional.empty();
            }

            List<Role> result = new LinkedList<>();
            for (String type : Arrays.asList(ROLE_GROUP, ROLE_APPROLE)) {
                JsonArray types = jsonObject.getJsonArray(type);
                if (null != types) {
                    for (int i = 0; i < types.size(); i++) {
                        JsonObject typeJson = types.getJsonObject(i);
                        String name = typeJson.getString("display");
                        String id = typeJson.getString("value");
                        String ref = typeJson.getString("$ref");

                        Role role = Role.builder()
                                .name(name)
                                .addAttribute("type", type)
                                .addAttribute("id", id)
                                .addAttribute("ref", ref)
                                .build();

                        result.add(role);
                    }
                }
            }

            return Optional.of(result);
        } else {
            LOGGER.warning("Cannot read groups for user \""
                                   + subjectName
                                   + "\". Response code: "
                                   + groupResponse.getStatus()
                                   + ", entity: "
                                   + groupResponse.readEntity(String.class));
            return Optional.empty();
        }
    }

    protected OidcConfig oidcConfig() {
        return oidcConfig;
    }

    protected String defaultIdcsSubjectType() {
        return defaultIdcsSubjectType;
    }

    /**
     * Fluent API builder for {@link io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderBase}.
     */
    public static class Builder<B extends Builder<B>> {

        private final Set<SubjectType> supportedTypes = EnumSet.noneOf(SubjectType.class);
        private String defaultIdcsSubjectType = IDCS_SUBJECT_TYPE_USER;

        private OidcConfig oidcConfig;

        @SuppressWarnings("unchecked")
        private B me = (B) this;

        protected Builder() {
        }

        /**
         * Update this builder state from configuration.
         * Expects:
         * <ul>
         * <li>oidc-config to load an instance of {@link io.helidon.security.providers.oidc.common.OidcConfig}</li>
         * <li>cache-config (optional) to load instances of {@link io.helidon.security.providers.common.EvictableCache} for
         * caching</li>
         * <li>default-idcs-subject-type to use when not defined in a JWT, either {@value #IDCS_SUBJECT_TYPE_USER} or
         *      {@link #IDCS_SUBJECT_TYPE_CLIENT}, defaults to {@value #IDCS_SUBJECT_TYPE_USER}</li>
         * </ul>
         *
         * @param config current node must have "oidc-config" as one of its children
         * @return updated builder instance
         */
        public B config(Config config) {
            config.get("oidc-config").as(OidcConfig.class).ifPresent(this::oidcConfig);
            config.get("subject-types").asList(SubjectType.class).ifPresent(list -> list.forEach(this::addSubjectType));
            config.get("default-idcs-subject-type").asString().ifPresent(this::defaultIdcsSubjectType);
            return me;
        }

        /**
         * Use explicit {@link io.helidon.security.providers.oidc.common.OidcConfig} instance, e.g. when using it also for OIDC
         * provider.
         *
         * @param config oidc specific configuration, must have at least identity endpoint and client credentials configured
         * @return updated builder instance
         */
        public B oidcConfig(OidcConfig config) {
            this.oidcConfig = config;
            return me;
        }

        protected OidcConfig oidcConfig() {
            return oidcConfig;
        }

        /**
         * Configure supported subject types.
         * By default {@link io.helidon.security.SubjectType#USER} is used if none configured.
         *
         * @param types types to configure as supported for mapping
         * @return updated builder instance
         */
        public B subjectTypes(SubjectType... types) {
            this.supportedTypes.clear();
            this.supportedTypes.addAll(Arrays.asList(types));
            return me;
        }

        /**
         * Configure subject type to use when requesting roles from IDCS.
         * Can be either {@link #IDCS_SUBJECT_TYPE_USER} or {@link #IDCS_SUBJECT_TYPE_CLIENT}.
         * Defaults to {@link #IDCS_SUBJECT_TYPE_USER}.
         *
         * @param subjectType type of subject to use when requesting roles from IDCS
         * @return udpated builder instance
         */
        public B defaultIdcsSubjectType(String subjectType) {
            this.defaultIdcsSubjectType = subjectType;
            return me;
        }

        /**
         * Add a supported subject type.
         * If none added, {@link io.helidon.security.SubjectType#USER} is used.
         * If any added, only the ones added will be used (e.g. if you want to use
         * both {@link io.helidon.security.SubjectType#USER} and {@link io.helidon.security.SubjectType#SERVICE},
         * both need to be added.
         *
         * @param type subject type to add to the list of supported types
         * @return updated builder instance
         */
        public B addSubjectType(SubjectType type) {
            this.supportedTypes.add(type);
            return me;
        }
    }

    protected static class AppToken {
        private final WebTarget tokenEndpoint;
        // caching application token (as that can be re-used for group requests)
        private Optional<String> tokenContent = Optional.empty();
        private Jwt appJwt;

        public AppToken(WebTarget tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
        }

        public synchronized Optional<String> getToken() {
            if (null == appJwt) {
                fromServer();
            } else {
                if (!appJwt.validate(Jwt.defaultTimeValidators()).isValid()) {
                    fromServer();
                }
            }
            return tokenContent;
        }

        private void fromServer() {
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

                this.tokenContent = Optional.of(accessToken);
                this.appJwt = signedJwt.getJwt();
            } else {
                LOGGER.severe("Failed to obtain access token for application to read groups"
                                      + " from IDCS. Response code: " + tokenResponse.getStatus() + ", entity: "
                                      + tokenResponse.readEntity(String.class));
                this.tokenContent = Optional.empty();
                this.appJwt = null;
            }
        }
    }

}
