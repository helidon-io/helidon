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

package io.helidon.security.providers.httpauth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.OutboundSecurityResponse;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.OutboundSecurityProvider;
import io.helidon.security.spi.SynchronousProvider;

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
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile("(.*):(.*)");
    private static final char[] EMPTY_PASSWORD = new char[0];

    private final UserStore userStore;
    private final String realm;
    private final SubjectType subjectType;

    private HttpBasicAuthProvider(Builder builder) {
        this.userStore = builder.userStore;
        this.realm = builder.realm;
        this.subjectType = builder.subjectType;
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

    private static OutboundSecurityResponse toBasicAuthOutbound(UserStore.User user) {
        String b64 = Base64.getEncoder()
                .encodeToString((user.login() + ":" + new String(user.password())).getBytes(StandardCharsets.UTF_8));
        String basicAuthB64 = "basic " + b64;
        return OutboundSecurityResponse
                .withHeaders(CollectionsHelper.mapOf("Authorization", CollectionsHelper.listOf(basicAuthB64)));
    }

    @Override
    public boolean isOutboundSupported(ProviderRequest providerRequest,
                                       SecurityEnvironment outbondEnv,
                                       EndpointConfig outboundEp) {

        // explicitly overridden username and/or password
        if (outboundEp.abacAttributeNames().contains(EP_PROPERTY_OUTBOUND_USER)) {
            return true;
        }

        SecurityContext secContext = providerRequest.securityContext();

        boolean userSupported = secContext.user()
                .map(user -> user.privateCredential(UserStore.User.class).isPresent()).orElse(false);

        boolean serviceSupported = secContext.service()
                .map(user -> user.privateCredential(UserStore.User.class).isPresent()).orElse(false);

        return userSupported || serviceSupported;
    }

    @Override
    protected OutboundSecurityResponse syncOutbound(ProviderRequest providerRequest,
                                                    SecurityEnvironment outboundEnv,
                                                    EndpointConfig outboundEp) {

        // first resolve user to use
        Optional<Object> maybeUsername = outboundEp.abacAttribute(EP_PROPERTY_OUTBOUND_USER);
        if (maybeUsername.isPresent()) {
            String username = maybeUsername.get().toString();

            UserStore.User user = userStore.user(username).orElseGet(() -> userFromEndpoint(username, outboundEp));

            return toBasicAuthOutbound(user);
        }

        // and if not present, use the one from request
        SecurityContext secContext = providerRequest.securityContext();

        return OptionalHelper.from(secContext.user()
                                           .flatMap(user -> user.privateCredential(UserStore.User.class)))
                .or(() -> secContext.service().flatMap(service -> service.privateCredential(UserStore.User.class)))
                .asOptional()
                .map(user -> {
                    Optional<Object> password = outboundEp.abacAttribute(EP_PROPERTY_OUTBOUND_PASSWORD);
                    if (password.isPresent()) {
                        return toBasicAuthOutbound(new UserStore.User() {
                            @Override
                            public String login() {
                                return user.login();
                            }

                            @Override
                            public char[] password() {
                                return password.map(String::valueOf).map(String::toCharArray).orElse(EMPTY_PASSWORD);
                            }
                        });
                    } else {
                        return toBasicAuthOutbound(user);
                    }
                })
                .orElseGet(OutboundSecurityResponse::abstain);
    }

    private UserStore.User userFromEndpoint(String username, EndpointConfig outboundEp) {
        return new UserStore.User() {
            @Override
            public String login() {
                return username;
            }

            @Override
            public char[] password() {
                return outboundEp.abacAttribute(EP_PROPERTY_OUTBOUND_PASSWORD)
                        .map(String::valueOf)
                        .map(String::toCharArray)
                        .orElse(EMPTY_PASSWORD);
            }
        };
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        Map<String, List<String>> headers = providerRequest.env().headers();
        List<String> authorizationHeader = headers.get(HEADER_AUTHENTICATION);

        if (null == authorizationHeader) {
            return fail("No " + HEADER_AUTHENTICATION + " header");
        }

        return authorizationHeader.stream()
                .filter(header -> header.toLowerCase().startsWith(BASIC_PREFIX))
                .findFirst()
                .map(this::validateBasicAuth)
                .orElseGet(() -> fail("Authorization header does not contain basic authentication: " + authorizationHeader));
    }

    private AuthenticationResponse validateBasicAuth(String basicAuthHeader) {
        String b64 = basicAuthHeader.substring(BASIC_PREFIX.length());

        String usernameAndPassword;
        try {
            usernameAndPassword = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // not a base64 encoded string
            return fail("Basic authentication header with invalid content - not base64 encoded");
        }

        Matcher matcher = CREDENTIAL_PATTERN.matcher(usernameAndPassword);
        if (!matcher.matches()) {
            LOGGER.finest(() -> "Basic authentication header with invalid content: " + usernameAndPassword);
            return fail("Basic authentication header with invalid content");
        }

        final String username = matcher.group(1);
        final char[] password = matcher.group(2).toCharArray();

        return userStore.user(username)
                .map(user -> {
                    if (Arrays.equals(password, user.password())) {
                        // yay, correct user and password!!!
                        if (subjectType == SubjectType.USER) {
                            return AuthenticationResponse.success(buildSubject(user));
                        } else {
                            return AuthenticationResponse.successService(buildSubject(user));
                        }
                    } else {
                        return fail("Invalid username or password");
                    }
                })
                .orElse(fail("Invalid username or password"));
    }

    private AuthenticationResponse fail(String message) {
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

    private Subject buildSubject(UserStore.User user) {
        Subject.Builder builder = Subject.builder()
                .principal(Principal.builder()
                                   .name(user.login())
                                   .build())
                .addPrivateCredential(UserStore.User.class, user);

        user.roles()
                .forEach(role -> builder.addGrant(Role.create(role)));

        return builder.build();
    }

    /**
     * {@link HttpBasicAuthProvider} fluent API builder.
     */
    public static final class Builder implements io.helidon.common.Builder<HttpBasicAuthProvider> {
        private static final UserStore EMPTY_STORE = login -> Optional.empty();
        private UserStore userStore = EMPTY_STORE;
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
            config.get("realm").asString().ifPresent(this::realm);
            config.get("principal-type").asString().as(SubjectType::valueOf).ifPresent(this::subjectType);

            // now users may not be configured at all
            Config usersConfig = config.get("users");
            if (usersConfig.exists()) {
                // or it may be jst an empty list (e.g. users: with no subnodes).
                if (!usersConfig.isLeaf()) {
                    userStore(usersConfig.as(ConfigUserStore::create)
                                              .orElseThrow(() -> new HttpAuthException(
                                                      "No users configured! Key \"users\" must be in configuration")));
                }
            }
            return this;
        }

        @Override
        public HttpBasicAuthProvider build() {
            // intentional instance equality
            if (userStore == EMPTY_STORE) {
                LOGGER.info("Basic authentication configured with no users. Inbound will always fail, outbound would work"
                                    + "only with explicit username and password");
            }
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
         * Set user store to obtain passwords and roles based on logins.
         *
         * @param store User store to use
         * @return updated builder instance
         */
        public Builder userStore(UserStore store) {
            this.userStore = store;
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
    }
}
