/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

package io.helidon.security.abac.scope;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Grant;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityLevel;
import io.helidon.security.providers.abac.AbacAnnotation;
import io.helidon.security.providers.abac.AbacValidatorConfig;
import io.helidon.security.providers.abac.spi.AbacValidator;

/**
 * ABAC validator for OAuth2 scopes.
 */
public final class ScopeValidator implements AbacValidator<ScopeValidator.ScopesConfig> {
    /**
     * Use this type when constructing a {@link Grant}, so this validator can accept it as a scope.
     */
    public static final String SCOPE_GRANT_TYPE = "scope";

    private final boolean useOrOperator;

    private ScopeValidator(Builder builder) {
        this.useOrOperator = builder.useOrOperator;
    }

    /**
     * Create a fluent API builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance of scope validator with default configuration.
     *
     * @return scope validator that uses "AND" operator for required scopes
     */
    public static ScopeValidator create() {
        return ScopeValidator.builder().build();
    }

    /**
     * Create a new validator instance from configuration.
     *
     * @param config configuration on the key of this provider
     * @return scope validator instance
     */
    public static ScopeValidator create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public Class<ScopesConfig> configClass() {
        return ScopesConfig.class;
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(Scope.class, Scopes.class);
    }

    @Override
    public String configKey() {
        return "scopes";
    }

    @Override
    public ScopesConfig fromConfig(Config config) {
        return ScopesConfig.create(config);
    }

    @Override
    public ScopesConfig fromAnnotations(EndpointConfig endpointConfig) {
        List<Scope> scopes = new ArrayList<>();
        for (SecurityLevel securityLevel : endpointConfig.securityLevels()) {
            for (EndpointConfig.AnnotationScope scope : EndpointConfig.AnnotationScope.values()) {
                List<Annotation> annotations = new ArrayList<>();
                for (Class<? extends Annotation> annotation : supportedAnnotations()) {
                    annotations.addAll(securityLevel.filterAnnotations(annotation, scope));
                }
                for (Annotation annot : annotations) {
                    if (annot instanceof Scopes) {
                        scopes.addAll(Arrays.asList(((Scopes) annot).value()));
                    } else if (annot instanceof Scope) {
                        scopes.add((Scope) annot);
                    }
                }
            }
        }

        return ScopesConfig.create(scopes);
    }

    @Override
    public void validate(ScopesConfig config, Errors.Collector collector, ProviderRequest request) {
        request.subject()
                .ifPresentOrElse(
                        subject -> {
                            Set<String> requiredScopes = new LinkedHashSet<>(config.requiredScopes());
                            int origRequired = requiredScopes.size();

                            if (origRequired == 0) {
                                collector.hint(this, "There are no required scopes for current request.");
                                return;
                            }

                            List<Grant> userScopes = subject.grantsByType(SCOPE_GRANT_TYPE);

                            // remove from required scopes
                            userScopes.stream().map(Grant::getName).forEach(requiredScopes::remove);
                            int remainingRequired = requiredScopes.size();

                            if (remainingRequired == origRequired) {
                                collector.fatal(this,
                                                "Access requires scopes: " + config.requiredScopes() + ", yet the user is in "
                                                        + "neither of them: " + userScopes);
                                return;
                            }

                            if (remainingRequired == 0) {
                                // user is in all required scopes
                                return;
                            }

                            if (useOrOperator) {
                                // this is sufficient - user is in at least one scope
                                return;
                            }

                            collector.fatal(this, "User is not in all required scopes: " + config.requiredScopes() + ", user's "
                                    + "scopes: " + userScopes);

                        }, () -> {
                            List<String> requiredScopes = config.requiredScopes();
                            if (!requiredScopes.isEmpty()) {
                                collector.fatal(this, "User not logged int. Required scopes: " + requiredScopes);
                            }
                        }
                );
    }

    /**
     * Scope annotation. You can have more than one scope annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @AbacAnnotation
    @Repeatable(Scopes.class)
    public @interface Scope {
        /**
         * Name of scope the user must have to access this resource.
         *
         * @return scope name
         */
        String value();
    }

    /**
     * Repeatable annotation for {@link Scope}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    public @interface Scopes {
        /**
         * Repeatable annotation holder.
         *
         * @return repeatable annotation
         */
        Scope[] value();
    }

    /**
     * A fluent API builder for {@link ScopeValidator}.
     */
    public static final class Builder implements io.helidon.common.Builder<ScopeValidator> {
        private boolean useOrOperator = false;

        private Builder() {
        }

        @Override
        public ScopeValidator build() {
            return new ScopeValidator(this);
        }

        /**
         * Whether to use "OR" or "AND" (default) operator.
         *
         * @param useOrOperator set to true to validate "at least one scope", set to false to validate "in all scopes", defaults
         *                      to false
         * @return updated builder instance
         */
        public Builder useOrOperator(boolean useOrOperator) {
            this.useOrOperator = useOrOperator;
            return this;
        }

        /**
         * Update builder from configuration.
         *
         * @param config config located on key of this validator
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("operator").asString().map("OR"::equals).ifPresent(this::useOrOperator);
            return this;
        }
    }

    /**
     * Configuration custom class for scope validator.
     */
    public static final class ScopesConfig implements AbacValidatorConfig {
        private final List<String> requiredScopes;

        private ScopesConfig(List<String> scopes) {
            this.requiredScopes = scopes;
        }

        /**
         * Create an instance from an array of scopes.
         *
         * @param scopes scopes required
         * @return configuration based on this list of scopes
         */
        public static ScopesConfig create(String... scopes) {
            return new ScopesConfig(List.of(scopes));
        }

        /**
         * Create an instance from a list of annotations.
         *
         * @param scopes scope annotations
         * @return configuration based on the list
         */
        public static ScopesConfig create(List<Scope> scopes) {
            List<String> allScopes = new ArrayList<>();

            for (Scope scope : scopes) {
                allScopes.add(scope.value());
            }
            return new ScopesConfig(allScopes);
        }

        /**
         * Create an instance from configuration (of endpoint).
         *
         * @param config config located on the key of this validator
         * @return configuration based on the config
         */
        public static ScopesConfig create(Config config) {
            return new ScopesConfig(config.asList(String.class).orElse(List.of()));
        }

        /**
         * Which scopes are required.
         *
         * @return list of scopes in order of definition
         */
        public List<String> requiredScopes() {
            return Collections.unmodifiableList(requiredScopes);
        }
    }
}
