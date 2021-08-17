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
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.JsonArray;
import javax.json.JsonObject;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.common.http.FormParams;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
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
import io.helidon.security.jwt.Validator;
import io.helidon.security.providers.oidc.common.OidcConfig;
import io.helidon.security.spi.SubjectMappingProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;

import static io.helidon.security.providers.oidc.common.OidcConfig.postJsonResponse;

/**
 * Common functionality for IDCS role mapping using reactive {@link io.helidon.webclient.WebClient}.
 */
public abstract class IdcsRoleMapperRxProviderBase implements SubjectMappingProvider {
    /**
     * User subject type used when requesting roles from IDCS.
     * An attempt is made to obtain it from JWT claim {@code sub_type}. If not defined,
     * default is used as configured in {@link IdcsRoleMapperRxProviderBase.Builder}.
     */
    public static final String IDCS_SUBJECT_TYPE_USER = "user";
    /**
     * Client subject type used when requesting roles from IDCS.
     * An attempt is made to obtain it from JWT claim {@code sub_type}. If not defined,
     * default is used as configured in {@link IdcsRoleMapperRxProviderBase.Builder}.
     */
    public static final String IDCS_SUBJECT_TYPE_CLIENT = "client";
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
    private static final Logger LOGGER = Logger.getLogger(IdcsRoleMapperRxProviderBase.class.getName());

    private final Set<SubjectType> supportedTypes = EnumSet.noneOf(SubjectType.class);
    private final OidcConfig oidcConfig;
    private final String defaultIdcsSubjectType;

    /**
     * Configures the needed fields from the provided builder.
     *
     * @param builder builder with oidcConfig and other needed fields.
     */
    protected IdcsRoleMapperRxProviderBase(Builder<?> builder) {
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

        if (maybeService.isEmpty() && maybeUser.isEmpty()) {
            return CompletableFuture.completedStage(previousResponse);
        }

        // create a new response
        AuthenticationResponse.Builder builder = AuthenticationResponse.builder();

        CompletionStage<Subject> result;
        if (maybeUser.isPresent()) {
            if (supportedTypes.contains(SubjectType.USER)) {
                // service will be done after use
                result = enhance(authenticatedRequest, previousResponse, maybeUser.get())
                        .thenApply(it -> {
                            builder.user(it);
                            return it;
                        });
            } else {
                builder.user(maybeUser.get());
                result = CompletableFuture.completedStage(null);
            }
        } else {
            // if no user, immediately do service (or nothing, if it is not present as well)
            result = CompletableFuture.completedStage(null);
        }
        if (maybeService.isPresent()) {
            if (supportedTypes.contains(SubjectType.SERVICE)) {
                // enhance service after any previous operation is finished
                result = result.thenCompose(ignored -> enhance(authenticatedRequest, previousResponse, maybeService.get()))
                        .thenApply(it -> {
                            builder.service(it);
                            return it;
                        });
            } else {
                builder.service(maybeService.get());
            }
        }
        return result.thenApply(ignored -> {
            previousResponse.description().ifPresent(builder::description);
            builder.requestHeaders(previousResponse.requestHeaders());

            return builder.build();
        });
    }

    /**
     * Enhance subject with IDCS roles, reactive.
     *
     * @param request provider request
     * @param previousResponse authenticated response
     * @param subject subject to enhance
     * @return future with enhanced subject
     */
    protected abstract CompletionStage<Subject> enhance(ProviderRequest request,
                                                        AuthenticationResponse previousResponse,
                                                        Subject subject);

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

    protected Single<List<? extends Grant>> processRoleRequest(WebClientRequestBuilder request,
                                                               Object entity,
                                                               String subjectName) {
        return postJsonResponse(request,
                                entity,
                                json -> processServerResponse(json, subjectName),
                                (status, errorEntity) -> {
                                    LOGGER.warning("Cannot read groups for user \""
                                                           + subjectName
                                                           + "\". Response code: "
                                                           + status
                                                           + (
                                            status == Http.Status.UNAUTHORIZED_401 ? ", make sure your IDCS client has role "
                                                    + "\"Authenticator Client\" added on the client configuration page" : "")
                                                           + ", error entity: " + errorEntity);
                                    return Optional.of(List.of());
                                },
                                (t, errorMessage) -> {
                                    LOGGER.log(Level.WARNING, "Cannot read groups for user \""
                                                       + subjectName
                                                       + "\". Error message: " + errorMessage,
                                               t);
                                    return Optional.of(List.of());
                                });
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

    private List<? extends Grant> processServerResponse(JsonObject jsonObject, String subjectName) {
        JsonArray groups = jsonObject.getJsonArray("groups");
        JsonArray appRoles = jsonObject.getJsonArray("appRoles");

        if ((null == groups) && (null == appRoles)) {
            LOGGER.finest(() -> "Neither groups nor app roles found for user " + subjectName);
            return List.of();
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

        return result;
    }

    /**
     * Fluent API builder for {@link IdcsRoleMapperRxProviderBase}.
     * @param <B> Type of the extending builder
     */
    public static class Builder<B extends Builder<B>> {

        private final Set<SubjectType> supportedTypes = EnumSet.noneOf(SubjectType.class);
        @SuppressWarnings("unchecked")
        private final B me = (B) this;
        private String defaultIdcsSubjectType = IDCS_SUBJECT_TYPE_USER;
        private OidcConfig oidcConfig;

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
     * Reactive token for app access to IDCS.
     */
    protected static class AppTokenRx {
        private static final List<Validator<Jwt>> TIME_VALIDATORS = Jwt.defaultTimeValidators();

        private final AtomicReference<CompletableFuture<AppTokenData>> token = new AtomicReference<>();
        private final WebClient webClient;
        private final URI tokenEndpointUri;

        protected AppTokenRx(WebClient webClient, URI tokenEndpointUri) {
            this.webClient = webClient;
            this.tokenEndpointUri = tokenEndpointUri;
        }

        protected CompletionStage<Optional<String>> getToken(RoleMapTracing tracing) {
            final CompletableFuture<AppTokenData> currentTokenData = token.get();
            if (currentTokenData == null) {
                CompletableFuture<AppTokenData> future = new CompletableFuture<>();
                if (token.compareAndSet(null, future)) {
                    fromServer(tracing, future);
                } else {
                    // another thread "stole" the data, return its future
                    future = token.get();
                }
                return future.thenApply(AppTokenData::tokenContent);
            }
            // there is an existing value
            return currentTokenData.thenCompose(tokenData -> {
                Jwt jwt = tokenData.appJwt();
                if (jwt == null || !tokenData.appJwt().validate(TIME_VALIDATORS).isValid()) {
                    // it is not valid - we must get a new value
                    CompletableFuture<AppTokenData> future = new CompletableFuture<>();
                    if (token.compareAndSet(currentTokenData, future)) {
                        fromServer(tracing, future);
                    } else {
                        future = token.get();
                    }
                    return future.thenApply(AppTokenData::tokenContent);
                } else {
                    // present and valid
                    return CompletableFuture.completedFuture(tokenData.tokenContent());
                }
            });
        }

        private void fromServer(RoleMapTracing tracing, CompletableFuture<AppTokenData> future) {
            FormParams params = FormParams.builder()
                    .add("grant_type", "client_credentials")
                    .add("scope", "urn:opc:idm:__myscopes__")
                    .build();

            // use current span context as a parent for client outbound
            // using a custom child context, so we do not replace the parent in the current context
            Context parentContext = Contexts.context().orElseGet(Contexts::globalContext);
            Context childContext = Context.builder()
                    .parent(parentContext)
                    .build();

            tracing.findParent()
                    .ifPresent(childContext::register);

            WebClientRequestBuilder request = webClient.post()
                    .uri(tokenEndpointUri)
                    .context(childContext)
                    .accept(io.helidon.common.http.MediaType.APPLICATION_JSON);

            postJsonResponse(request,
                             params,
                             json -> {
                                 String accessToken = json.getString(ACCESS_TOKEN_KEY);
                                 LOGGER.finest(() -> "Access token: " + accessToken);
                                 SignedJwt signedJwt = SignedJwt.parseToken(accessToken);
                                 return new AppTokenData(accessToken, signedJwt.getJwt());
                             },
                             (status, message) -> {
                                 LOGGER.log(Level.SEVERE, "Failed to obtain access token for application to read "
                                         + "groups from IDCS. Status: " + status + ", error message: " + message);
                                 return Optional.of(new AppTokenData());
                             },
                             (t, message) -> {
                                 LOGGER.log(Level.SEVERE, "Failed to obtain access token for application to read "
                                         + "groups from IDCS. Failed with exception: " + message, t);
                                 return Optional.of(new AppTokenData());
                             })
                    .forSingle(future::complete);
        }
    }

    private static final class AppTokenData {
        private final Optional<String> tokenContent;
        private final Jwt appJwt;

        AppTokenData() {
            this.tokenContent = Optional.empty();
            this.appJwt = null;
        }

        AppTokenData(String tokenContent, Jwt appJwt) {
            this.tokenContent = Optional.ofNullable(tokenContent);
            this.appJwt = appJwt;
        }

        Optional<String> tokenContent() {
            return tokenContent;
        }

        Jwt appJwt() {
            return appJwt;
        }
    }
}
