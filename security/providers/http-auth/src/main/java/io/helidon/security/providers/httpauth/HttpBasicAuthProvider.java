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

package io.helidon.security.providers.httpauth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.SecurityResponse;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.common.OutboundConfig;
import io.helidon.security.providers.common.OutboundTarget;
import io.helidon.security.providers.httpauth.spi.UserStoreService;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;
import io.helidon.security.util.TokenHandler;

/**
 * Http authentication security provider.
 * Provides support for username and password authentication, with support for roles list.
 */
public class HttpBasicAuthProvider extends SynchronousProvider implements AuthenticationProvider, OutboundSecurityProvider {
    /**
     * Configure this for outbound requests to override user to use.
     */
    public static final String EP_PROPERTY_OUTBOUND_USER = "io.helidon.security.outbound.user";

    /**
     * Configure this for outbound requests to override password to use.
     */
    public static final String EP_PROPERTY_OUTBOUND_PASSWORD = "io.helidon.security.outbound.password";

    static final String HEADER_AUTHENTICATION_REQUIRED = "WWW-Authenticate";
    static final String HEADER_AUTHENTICATION = "authorization";
    static final String BASIC_PREFIX = "basic ";

    private static final Logger LOGGER = Logger.getLogger(HttpBasicAuthProvider.class.getName());
    static final Pattern CREDENTIAL_PATTERN = Pattern.compile("(.*?):(.*)");

    private final List<SecureUserStore> userStores;
    private final boolean optional;
    private final String realm;
    private final SubjectType subjectType;
    private final OutboundConfig outboundConfig;
    private final boolean outboundTargetsExist;

    HttpBasicAuthProvider(Builder builder) {
        this.userStores = new LinkedList<>(builder.userStores);
        this.optional = builder.optional;
        this.realm = builder.realm;
        this.subjectType = builder.subjectType;
        this.outboundConfig = builder.outboundBuilder.build();
        this.outboundTargetsExist = outboundConfig.targets().size() > 0;
    }

    /**
     * Get a builder instance to construct a new security provider.
     * Alternative approach is {@link #create(Config)} (or {@link HttpBasicAuthProvider#create(Config)}).
     *
     * @return builder to fluently construct Basic security provider
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Load this provider from configuration.
     *
     * @param config Configuration located at this provider's configuration (e.g. child is either http-basic-auth or
     *               http-digest-auth)
     * @return instance of provider configured from provided config
     */
    public static HttpBasicAuthProvider create(Config config) {
        return builder().config(config).build();
    }

    private static OutboundSecurityResponse toBasicAuthOutbound(SecurityEnvironment outboundEnv,
                                                                TokenHandler tokenHandler,
                                                                String username,
                                                                char[] password) {
        String b64 = Base64.getEncoder()
                .encodeToString((username + ":" + new String(password)).getBytes(StandardCharsets.UTF_8));

        Map<String, List<String>> headers = new HashMap<>(outboundEnv.headers());
        tokenHandler.addHeader(headers, b64);
        return OutboundSecurityResponse
                .withHeaders(headers);
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outbondEnv,
                                       EndpointConfig outboundEp) {

        // explicitly overridden username and/or password
        if (outboundEp.abacAttributeNames().contains(EP_PROPERTY_OUTBOUND_USER)) {
            return true;
        }

        return outboundTargetsExist;
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEp) {

        // explicit username in request properties
        Optional<Object> maybeUsername = outboundEp.abacAttribute(EP_PROPERTY_OUTBOUND_USER);
        if (maybeUsername.isPresent()) {
            String username = maybeUsername.get().toString();
            char[] password = passwordFromEndpoint(outboundEp);

            return toBasicAuthOutbound(outboundEnv,
                                       HttpBasicOutboundConfig.DEFAULT_TOKEN_HANDLER,
                                       username,
                                       password);
        }

        var target = outboundConfig.findTargetCustomObject(outboundEnv,
                                                           HttpBasicOutboundConfig.class,
                                                           HttpBasicOutboundConfig::create,
                                                           HttpBasicOutboundConfig::create);

        if (target.isEmpty()) {
            return OutboundSecurityResponse.abstain();
        }

        HttpBasicOutboundConfig outboundConfig = target.get();

        if (outboundConfig.hasExplicitUser()) {
            // use configured user
            return toBasicAuthOutbound(outboundEnv,
                                       outboundConfig.tokenHandler(),
                                       outboundConfig.explicitUser(),
                                       outboundConfig.explicitPassword());
        } else {
            // propagate current user (if possible)
            SecurityContext secContext = providerRequest.securityContext();
            // first try user
            Optional<BasicPrivateCredentials> creds = secContext.user()
                    .flatMap(this::credentialsFromSubject);
            if (creds.isEmpty()) {
                // if not present, try service
                creds = secContext.service()
                        .flatMap(this::credentialsFromSubject);
            }

            Optional<char[]> overridePassword = outboundEp.abacAttribute(EP_PROPERTY_OUTBOUND_PASSWORD)
                    .map(String::valueOf)
                    .map(String::toCharArray);

            return creds.map(credentials -> {
                char[] password = overridePassword.orElse(credentials.password);
                return toBasicAuthOutbound(outboundEnv,
                                           outboundConfig.tokenHandler(),
                                           credentials.username,
                                           password);
            }).orElseGet(OutboundSecurityResponse::abstain);
        }
    }

    private Optional<BasicPrivateCredentials> credentialsFromSubject(Subject subject) {
        return subject.privateCredential(BasicPrivateCredentials.class);
    }

    private char[] passwordFromEndpoint(EndpointConfig outboundEp) {
        return outboundEp.abacAttribute(EP_PROPERTY_OUTBOUND_PASSWORD)
                .map(String::valueOf)
                .map(String::toCharArray)
                .orElse(HttpBasicOutboundConfig.EMPTY_PASSWORD);
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        Map<String, List<String>> headers = providerRequest.env().headers();
        List<String> authorizationHeader = headers.get(HEADER_AUTHENTICATION);

        if (null == authorizationHeader) {
            return failOrAbstain("No " + HEADER_AUTHENTICATION + " header");
        }

        return authorizationHeader.stream()
                .filter(header -> header.toLowerCase().startsWith(BASIC_PREFIX))
                .findFirst()
                .map(this::validateBasicAuth)
                .orElseGet(() ->
                        failOrAbstain("Authorization header does not contain basic authentication: " + authorizationHeader));
    }

    private AuthenticationResponse validateBasicAuth(String basicAuthHeader) {
        String b64 = basicAuthHeader.substring(BASIC_PREFIX.length());

        String usernameAndPassword;
        try {
            usernameAndPassword = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // not a base64 encoded string
            return failOrAbstain("Basic authentication header with invalid content - not base64 encoded");
        }

        Matcher matcher = CREDENTIAL_PATTERN.matcher(usernameAndPassword);
        if (!matcher.matches()) {
            LOGGER.finest(() -> "Basic authentication header with invalid content: " + usernameAndPassword);
            return failOrAbstain("Basic authentication header with invalid content");
        }

        final String username = matcher.group(1);
        final char[] password = matcher.group(2).toCharArray();

        Optional<SecureUserStore.User> foundUser = Optional.empty();
        for (SecureUserStore userStore : userStores) {
            foundUser = userStore.user(username);
            if (foundUser.isPresent()) {
                // find first user from stores
                break;
            }
        }

        return foundUser.map(user -> {
            if (user.isPasswordValid(password)) {
                if (subjectType == SubjectType.USER) {
                    return AuthenticationResponse.success(buildSubject(user, password));
                }
                return AuthenticationResponse.successService(buildSubject(user, password));
            } else {
                return invalidUser();
            }
        }).orElseGet(this::invalidUser);
    }

    private AuthenticationResponse invalidUser() {
        // extracted to method to make sure we return the same message for invalid user and password
        // DO NOT change this - it is a security problem if the message differs, as it gives too much information
        // to potential attacker
        return failOrAbstain("Invalid username or password");
    }

    private AuthenticationResponse failOrAbstain(String message) {
        if (optional)
            return AuthenticationResponse.builder()
                    .status(SecurityResponse.SecurityStatus.ABSTAIN)
                    .description(message)
                    .build();
        else
            return AuthenticationResponse.builder()
                    .statusCode(401)
                    .responseHeader(HEADER_AUTHENTICATION_REQUIRED, buildChallenge())
                    .status(AuthenticationResponse.SecurityStatus.FAILURE)
                    .description(message)
                    .build();
    }

    private String buildChallenge() {
        return "Basic realm=\"" + realm + "\"";
    }

    private Subject buildSubject(SecureUserStore.User user, char[] password) {
        Subject.Builder builder = Subject.builder()
                .principal(Principal.builder()
                                   .name(user.login())
                                   .build())
                .addPrivateCredential(BasicPrivateCredentials.class, new BasicPrivateCredentials(user.login(), password));

        user.roles()
                .forEach(role -> builder.addGrant(Role.create(role)));

        return builder.build();
    }

    /**
     * {@link HttpBasicAuthProvider} fluent API builder.
     */
    public static final class Builder implements io.helidon.common.Builder<HttpBasicAuthProvider> {
        private final List<SecureUserStore> userStores = new LinkedList<>();
        private final OutboundConfig.Builder outboundBuilder = OutboundConfig.builder();

        private boolean optional = false;
        private String realm = "helidon";
        private SubjectType subjectType = SubjectType.USER;

        private Builder() {
        }

        /**
         * Update this builder from configuration.
         * @param config configuration to read, located on the node of the http basic authentication provider
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("optional").asBoolean().ifPresent(this::optional);
            config.get("realm").asString().ifPresent(this::realm);
            config.get("principal-type").asString().as(SubjectType::valueOf).ifPresent(this::subjectType);

            HelidonServiceLoader.Builder<UserStoreService> loader =
                    HelidonServiceLoader.builder(ServiceLoader.load(UserStoreService.class));

            // now users may not be configured at all
            Config usersConfig = config.get("users");
            if (usersConfig.exists()) {
                // or it may be jst an empty list (e.g. users: with no subnodes).
                if (!usersConfig.isLeaf()) {
                    loader.addService(new UserStoreService() {
                        @Override
                        public String configKey() {
                            return "users";
                        }

                        @Override
                        public SecureUserStore create(Config config) {
                            return usersConfig.as(ConfigUserStore::create)
                                    .orElseThrow(() -> new HttpAuthException(
                                            "No users configured! Key \"users\" must be in configuration"));
                        }
                    });
                }
            }

            // when creating an instance from configuration, we also want to load user stores from service loader
            loader.build()
                    .forEach(userStoreService -> {
                        addUserStore(userStoreService.create(config.get(userStoreService.configKey())));
                    });

            config.get("outbound").asList(OutboundTarget::create)
                    .ifPresent(it -> it.forEach(outboundBuilder::addTarget));

            return this;
        }

        @Override
        public HttpBasicAuthProvider build() {
            return new HttpBasicAuthProvider(this);
        }

        /**
         * Principal type this provider extracts (and also propagates).
         *
         * @param subjectType type of principal
         * @return updated builder instance
         */
        public Builder subjectType(SubjectType subjectType) {
            this.subjectType = subjectType;

            switch (subjectType) {
            case USER:
            case SERVICE:
                break;
            default:
                throw new SecurityException("Invalid configuration. Principal type not supported: " + subjectType);
            }

            return this;
        }

        /**
         * Add a user store to the list of stores used by this provider.
         *
         * @param store user store to add
         * @return updated builder instance
         */
        public Builder addUserStore(SecureUserStore store) {
            userStores.add(store);
            return this;
        }

        /**
         * Set user store to validate users.
         * Removes any other stores added through {@link #addUserStore(SecureUserStore)}.
         * @param store User store to use
         * @return updated builder instance
         */
        public Builder userStore(SecureUserStore store) {
            userStores.clear();
            userStores.add(store);
            return this;
        }

        /**
         * Set the realm to use when challenging users.
         *
         * @param realm security realm name to send to browser (or any other client) when unauthenticated
         * @return updated builder instance
         */
        public Builder realm(String realm) {
            this.realm = realm;
            return this;
        }

        /**
         * Whether authentication is required.
         * By default, request will fail if the authentication cannot be verified.
         * If set to false, request will process and this provider will abstain.
         *
         * @param optional whether authentication is optional (true) or required (false)
         * @return updated builder instance
         */
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Add a new outbound target to configure identity propagation or explicit username/password.
         *
         * @param target outbound target
         * @return updated builder instance
         */
        public Builder addOutboundTarget(OutboundTarget target) {
            this.outboundBuilder.addTarget(target);
            return this;
        }

    }

    // need to store this information to be able to propagate to outbound
    private static final class BasicPrivateCredentials {
        private final String username;
        private final char[] password;

        private BasicPrivateCredentials(String username, char[] password) {
            this.username = username;
            this.password = password;
        }
    }

}
