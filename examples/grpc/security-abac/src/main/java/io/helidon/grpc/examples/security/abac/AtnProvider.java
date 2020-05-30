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

package io.helidon.grpc.examples.security.abac;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.security.AuthenticationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.spi.AuthenticationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Example authentication provider that reads annotation to create a subject.
 */
public class AtnProvider extends SynchronousProvider implements AuthenticationProvider {

    /**
     * The configuration key for this provider.
     */
    public static final String CONFIG_KEY = "atn";

    private final Config config;

    private AtnProvider(Config config) {
        this.config = config;
    }

    @Override
    protected AuthenticationResponse syncAuthenticate(ProviderRequest providerRequest) {
        EndpointConfig endpointConfig = providerRequest.endpointConfig();
        Config atnConfig = endpointConfig.config(CONFIG_KEY).orElse(null);
        Subject user = null;
        Subject service = null;
        List<Auth> list;

        Optional<AtnConfig> optional = providerRequest.endpointConfig().instance(AtnConfig.class);

        if (optional.isPresent()) {
            list = optional.get().auths();
        } else if (atnConfig != null && !atnConfig.isLeaf()) {
            list = atnConfig.asNodeList()
                            .map(this::fromConfig).orElse(Collections.emptyList());
        } else {
            list = fromAnnotations(endpointConfig);
        }

        for (Auth authentication : list) {
            if (authentication.type() == SubjectType.USER) {
                user = buildSubject(authentication);
            } else {
                service = buildSubject(authentication);
            }
        }

        return AuthenticationResponse.success(user, service);
    }

    private List<Auth> fromConfig(List<Config> configList) {
        return configList.stream()
                         .map(Auth::new)
                         .collect(Collectors.toList());
    }

    private List<Auth> fromAnnotations(EndpointConfig endpointConfig) {
        return endpointConfig.securityLevels()
                .stream()
                .flatMap(level -> level.combineAnnotations(Authentications.class, EndpointConfig.AnnotationScope.METHOD).stream())
                .map(Authentications::value)
                .flatMap(Arrays::stream)
                .map(Auth::new)
                .collect(Collectors.toList());
    }

    private Subject buildSubject(Auth authentication) {
        Subject.Builder subjectBuilder = Subject.builder();

        subjectBuilder.principal(Principal.create(authentication.principal()));

        Arrays.stream(authentication.roles())
                .map(Role::create)
                .forEach(subjectBuilder::addGrant);

        Arrays.stream(authentication.scopes())
                .map(scope -> Grant.builder().name(scope).type("scope").build())
                .forEach(subjectBuilder::addGrant);

        return subjectBuilder.build();
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Authentication.class);
    }

    /**
     * Create a {@link AtnProvider}.
     * @return  a {@link AtnProvider}
     */
    public static AtnProvider create() {
        return builder().build();
    }

    /**
     * Create a {@link AtnProvider}.
     *
     * @param config  the configuration for the {@link AtnProvider}
     *
     * @return  a {@link AtnProvider}
     */
    public static AtnProvider create(Config config) {
        return builder(config).build();
    }

    /**
     * Create a {@link AtnProvider.Builder}.
     * @return  a {@link AtnProvider.Builder}
     */
    public static Builder builder() {
        return builder(null);
    }

    /**
     * Create a {@link AtnProvider.Builder}.
     *
     * @param config  the configuration for the {@link AtnProvider}
     *
     * @return  a {@link AtnProvider.Builder}
     */
    public static Builder builder(Config config) {
        return new Builder(config);
    }

    /**
     * A builder that builds {@link AtnProvider} instances.
     */
    public static class Builder
            implements io.helidon.common.Builder<AtnProvider> {

        private Config config;

        private Builder(Config config) {
            this.config = config;
        }

        /**
         * Set the configuration for the {@link AtnProvider}.
         * @param config  the configuration for the {@link AtnProvider}
         * @return  this builder
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        @Override
        public AtnProvider build() {
            return new AtnProvider(config);
        }
    }

    /**
     * Authentication annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @Documented
    @Inherited
    @Repeatable(Authentications.class)
    public @interface Authentication {
        /**
         * Name of the principal.
         *
         * @return principal name
         */
        String value();

        /**
         * Type of the subject, defaults to user.
         *
         * @return type
         */
        SubjectType type() default SubjectType.USER;

        /**
         * Granted roles.
         * @return array of roles
         */
        String[] roles() default "";

        /**
         * Granted scopes.
         * @return array of scopes
         */
        String[] scopes() default "";
    }

    /**
     * Repeatable annotation for {@link Authentication}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @Documented
    @Inherited
    public @interface Authentications {
        /**
         * Repeating annotation.
         * @return annotations
         */
        Authentication[] value();
    }

    /**
     * A holder for authentication settings.
     */
    public static class Auth {
        private String principal;
        private SubjectType type = SubjectType.USER;
        private String[] roles;
        private String[] scopes;

        private Auth(Authentication authentication) {
            principal = authentication.value();
            type = authentication.type();
            roles = authentication.roles();
            scopes = authentication.scopes();
        }

        private Auth(Config config) {
            config.get("principal").ifExists(cfg -> principal = cfg.asString().get());
            config.get("type").ifExists(cfg -> type = SubjectType.valueOf(cfg.asString().get()));
            config.get("roles").ifExists(cfg -> roles = cfg.asList(String.class).get().toArray(new String[0]));
            config.get("scopes").ifExists(cfg -> scopes = cfg.asList(String.class).get().toArray(new String[0]));
        }

        private Auth(String principal, SubjectType type, String[] roles, String[] scopes) {
            this.principal = principal;
            this.type = type;
            this.roles = roles;
            this.scopes = scopes;
        }

        private String principal() {
            return principal;
        }

        private SubjectType type() {
            return type;
        }

        private String[] roles() {
            return roles;
        }

        private String[] scopes() {
            return scopes;
        }

        /**
         * Obtain a builder for building {@link Auth} instances.
         *
         * @param principal  the principal name
         *
         * @return a builder for building {@link Auth} instances.
         */
        public static Builder builder(String principal) {
            return new Auth.Builder(principal);
        }

        /**
         * A builder for building {@link Auth} instances.
         */
        public static class Builder
            implements io.helidon.common.Builder<Auth> {

            private final String principal;
            private SubjectType type = SubjectType.USER;
            private String[] roles;
            private String[] scopes;

            private Builder(String principal) {
                this.principal = principal;
            }

            /**
             * Set the {@link SubjectType}.
             * @param type  the {@link SubjectType}
             * @return  this builder
             */
            public Builder type(SubjectType type) {
                this.type = type;
                return this;
            }

            /**
             * Set the roles.
             * @param roles  the role names
             * @return  this builder
             */
            public Builder roles(String... roles) {
                this.roles = roles;
                return this;
            }

            /**
             * Set the scopes.
             * @param scopes  the scopes names
             * @return  this builder
             */
            public Builder scopes(String... scopes) {
                this.scopes = scopes;
                return this;
            }

            @Override
            public Auth build() {
                return new Auth(principal, type, roles, scopes);
            }
        }
    }

    /**
     * The configuration for a {@link AtnProvider}.
     */
    public static class AtnConfig {
        private final List<Auth> authData;

        private AtnConfig(List<Auth> list) {
            this.authData = list;
        }

        /**
         * Obtain the {@link List} of {@link Auth}s to use.
         *
         * @return  the {@link List} of {@link Auth}s to use
         */
        public List<Auth> auths() {
            return Collections.unmodifiableList(authData);
        }

        /**
         * Obtain a builder for building {@link AtnConfig} instances.
         *
         * @return a builder for building {@link AtnConfig} instances
         */
        public static AtnConfig.Builder builder() {
            return new Builder();
        }

        /**
         * A builder for building {@link AtnConfig} instances.
         */
        public static class Builder
                implements io.helidon.common.Builder<AtnConfig> {

            private final List<Auth> authData = new ArrayList<>();

            /**
             * Add an {@link Auth} instance.
             *
             * @param auth  the {@link Auth} to add
             *
             * @return  this builder
             *
             * @throws java.lang.NullPointerException if the {@link Auth} is null
             */
            public Builder addAuth(Auth auth) {
                authData.add(Objects.requireNonNull(auth));
                return this;
            }

            @Override
            public AtnConfig build() {
                return new AtnConfig(authData);
            }
        }
    }
}
