/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import javax.ws.rs.client.Invocation;
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
import io.helidon.security.integration.common.RoleMapTracing;
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

    /**
     * Json key for group roles to be retrieved from IDCS response.
     */
    protected static final String ROLE_GROUP = "groups";
    /**
     * Json key for app roles to be retrieved from IDCS response.
     */
    protected static final String ROLE_APPROLE = "appRoles";
    /**
     * Json key for token to be retrieved from IDCS response when requesting application token.
     */
    protected static final String ACCESS_TOKEN_KEY = "access_token";
    /**
     * Property sent with JAX-RS requests to override parent span context in outbound calls.
     * We cannot use the constant declared in {@code ClientTracingFilter}, as it is not a required dependency.
     */
    protected static final String PARENT_CONTEXT_CLIENT_PROPERTY = "io.helidon.tracing.span-context";

    private static final int STATUS_NOT_AUTHENTICATED = 401;

    private final Set<SubjectType> supportedTypes = EnumSet.noneOf(SubjectType.class);
    private final OidcConfig oidcConfig;
    private final String defaultIdcsSubjectType;

    /**
     * Configures the needed fields from the provided builder.
     *
     * @param builder builder with oidcConfig and other needed fields.
     */
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

    /**
     * Create a {@link java.util.concurrent.CompletionStage} with the provided response as its completion.
     *
     * @param response authentication response to complete with
     * @return stage completed with the response
     */
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

    /**
     * Process the server response to retrieve groups and app roles from it.
     *
     * @param groupResponse response from IDCS
     * @param subjectName name of the subject
     * @return list of grants obtained from the IDCS response
     */
    protected Optional<List<? extends Grant>> processServerResponse(Response groupResponse, String subjectName) {
        Response.StatusType statusInfo = groupResponse.getStatusInfo();
        if (statusInfo.getFamily() == Response.Status.Family.SUCCESSFUL) {
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
            if (statusInfo.getStatusCode() == STATUS_NOT_AUTHENTICATED) {
                // most likely not allowed to do this
                LOGGER.warning("Cannot read groups for user \""
                                       + subjectName
                                       + "\". Response code: "
                                       + groupResponse.getStatus()
                                       + ", make sure your IDCS client has role \"Authenticator Client\" added on the client"
                                       + " configuration page"
                                       + ", entity: "
                                       + groupResponse.readEntity(String.class));
            } else {
                LOGGER.warning("Cannot read groups for user \""
                                       + subjectName
                                       + "\". Response code: "
                                       + groupResponse.getStatus()
                                       + ", entity: "
                                       + groupResponse.readEntity(String.class));
            }

            return Optional.empty();
        }
    }

    /**
     * Access to {@link io.helidon.security.providers.oidc.common.OidcConfig} so the field is not duplicated by
     *  classes that extend this provider.
     *
     * @return open ID Connect configuration (also used to configure access to IDCS)
     */
    protected OidcConfig oidcConfig() {
        return oidcConfig;
    }

    /**
     * Default subject type to use when requesting data from IDCS.
     *
     * @return configured default subject type or {@link #IDCS_SUBJECT_TYPE_USER}
     */
    protected String defaultIdcsSubjectType() {
        return defaultIdcsSubjectType;
    }

    /**
     * Fluent API builder for {@link io.helidon.security.providers.idcs.mapper.IdcsRoleMapperProviderBase}.
     * @param <B> Type of the extending builder
     */
    public static class Builder<B extends Builder<B>> {

        private final Set<SubjectType> supportedTypes = EnumSet.noneOf(SubjectType.class);
        private String defaultIdcsSubjectType = IDCS_SUBJECT_TYPE_USER;

        private OidcConfig oidcConfig;

        @SuppressWarnings("unchecked")
        private B me = (B) this;

        /**
         * Default constructor.
         */
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
            config.get("oidc-config").ifExists(it -> {
                OidcConfig.Builder builder = OidcConfig.builder();
                // we do not need JWT validation at all
                builder.validateJwtWithJwk(false);
                // this is an IDCS specific extension
                builder.serverType("idcs");
                builder.config(it);

                oidcConfig(builder.build());
            });

            config.get("subject-types").asList(cfg -> cfg.asString().map(SubjectType::valueOf).get())
                    .ifPresent(list -> list.forEach(this::addSubjectType));
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

        /**
         * Get the configuration to access IDCS instance.
         * @return oidc config
         */
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
         * @return updated builder instance
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

    /**
     * A token for app access to IDCS.
     */
    protected static class AppToken {
        private final WebTarget tokenEndpoint;
        // caching application token (as that can be re-used for group requests)
        private Optional<String> tokenContent = Optional.empty();
        private Jwt appJwt;

        /**
         * Create a new token with a token endpoint.
         *
         * @param tokenEndpoint used to get a new token from IDCS
         */
        protected AppToken(WebTarget tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
        }

        /**
         * Get the token to use for requests to IDCS.
         * @param tracing tracing to use when requesting a new token from server
         * @return token content or empty if it could not be obtained
         */
        protected synchronized Optional<String> getToken(RoleMapTracing tracing) {
            if (null == appJwt) {
                fromServer(tracing);
            } else {
                if (!appJwt.validate(Jwt.defaultTimeValidators()).isValid()) {
                    fromServer(tracing);
                }
            }
            return tokenContent;
        }

        private void fromServer(RoleMapTracing tracing) {
            MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
            formData.putSingle("grant_type", "client_credentials");
            formData.putSingle("scope", "urn:opc:idm:__myscopes__");

            Invocation.Builder reqBuilder = tokenEndpoint.request();

            tracing.findParent()
                    .ifPresent(spanContext -> reqBuilder.property(PARENT_CONTEXT_CLIENT_PROPERTY, spanContext));

            Response tokenResponse = reqBuilder
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
