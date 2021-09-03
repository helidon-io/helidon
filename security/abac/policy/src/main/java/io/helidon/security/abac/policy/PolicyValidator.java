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

package io.helidon.security.abac.policy;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.Errors;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityLevel;
import io.helidon.security.abac.policy.spi.PolicyExecutor;
import io.helidon.security.abac.policy.spi.PolicyExecutorService;
import io.helidon.security.providers.abac.AbacAnnotation;
import io.helidon.security.providers.abac.AbacValidatorConfig;
import io.helidon.security.providers.abac.spi.AbacValidator;

/**
 * Abac validator based on a {@link PolicyStatement}. The statement itself is not resolved by this validator
 * and is delegated to another module implementing the {@link PolicyExecutor} obtained through a {@link PolicyExecutorService}
 * java service.
 * <p>
 * Implementations provided by Helidon security:
 * <ul>
 * <li>Java EE expression language support, artifact id: "helidon-security-abac-policy-el"</li>
 * </ul>
 *
 * Example of a policy statement:<br>
 * <code>&#64;PolicyStatement("${env.time.year &gt;= 2017 &amp;&amp; object.owner == subject.principal.id}")</code>
 */
public final class PolicyValidator implements AbacValidator<PolicyValidator.PolicyConfig> {
    private static final Logger LOGGER = Logger.getLogger(PolicyValidator.class.getName());

    private final List<PolicyExecutor> executors = new LinkedList<>();

    private PolicyValidator(Builder builder) {
        //first find all services
        HelidonServiceLoader<PolicyExecutorService> services =
                HelidonServiceLoader.create(ServiceLoader.load(PolicyExecutorService.class));

        for (PolicyExecutorService service : services) {
            executors.add(service.instantiate(builder.config.get(service.configKey())));
        }
        //then add explicit
        this.executors.addAll(builder.executors);
    }

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance from configuration.
     * Example:
     * <pre>
     * # configuration of this validator (current key in config passed to this instance)
     * policy-validator:
     *   # explicit validators - only needed if not implementing service interface {@link PolicyExecutorService}
     *   validators:
     *     - class: "io.helidon.security.abac.policy.DefaultPolicyValidator"
     *     - class: "..."
     *   # configuration of a policy executor - provide this name through {@link PolicyExecutorService#configKey()}
     *   my-custom-policy-engine:
     *     some-key: "some value"
     *     another-key: "another value"
     * </pre>
     *
     * @param config configuration to load this class from
     * @return a new instance from config
     */
    public static PolicyValidator create(Config config) {
        return builder().config(config).build();
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return Set.of(PolicyStatement.class);
    }

    @Override
    public Class<PolicyConfig> configClass() {
        return PolicyConfig.class;
    }

    @Override
    public String configKey() {
        return "policy-validator";
    }

    @Override
    public PolicyConfig fromConfig(Config config) {
        return PolicyConfig.builder()
                .config(config)
                .build();
    }

    @Override
    public PolicyConfig fromAnnotations(EndpointConfig endpointConfig) {
        PolicyConfig.Builder resultBuilder = PolicyConfig.builder();
        for (SecurityLevel securityLevel : endpointConfig.securityLevels()) {
            for (EndpointConfig.AnnotationScope scope : EndpointConfig.AnnotationScope.values()) {
                List<Annotation> annotations = new ArrayList<>();
                for (Class<? extends Annotation> annotation : supportedAnnotations()) {
                    annotations.addAll(securityLevel.filterAnnotations(annotation, scope));
                }
                for (Annotation annotation : annotations) {
                    if (annotation instanceof PolicyStatement) {
                        PolicyStatement statement = (PolicyStatement) annotation;
                        resultBuilder.from(PolicyConfig.builder().from(statement).build());
                    }
                }
            }
        }

        return resultBuilder.build();
    }

    @Override
    public void validate(PolicyConfig config, Errors.Collector collector, ProviderRequest request) {
        List<String> unvalidatedStatements = new LinkedList<>();
        boolean isValidated;

        for (String statement : config.policyStatements()) {
            isValidated = false;

            for (PolicyExecutor executor : executors) {
                if (executor.supports(statement, request)) {
                    executor.executePolicy(statement, collector, request);
                    isValidated = true;
                    break;
                }
            }

            if (!isValidated) {
                unvalidatedStatements.add(statement);
            }
        }

        if (!unvalidatedStatements.isEmpty()) {
            throw new SecurityException("Missing a policy executor for policy statement(s). Statements: " + unvalidatedStatements
                                                + ", known executors: " + executors);
        }
    }

    /**
     * Annotate resource classes, methods, application etc. with this annotation
     * to enforce validation of the policy statement defined.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @AbacAnnotation
    public @interface PolicyStatement {
        /**
         * The statement of this policy. Actual value depends on policy engine in use.
         *
         * @return policy statement value
         */
        String value();

        /**
         * By default, all policy statements defined on all levels (application, class, method, field) are executed
         * in order. If set to false, on the last policy in the list is executed (e.g. only on method for resource method
         * of Jersey integration, if it is defined on method).
         * NOTE: if there is no statement defined on current level, we will use a statement from nearest level above
         *
         * @return whether to inherit policies from annotations defined on higher level
         */
        boolean inherit() default true;
    }

    /**
     * A fluent API builder for {@link PolicyValidator}.
     */
    public static final class Builder implements io.helidon.common.Builder<PolicyValidator> {
        private final List<PolicyExecutor> executors = new LinkedList<>();
        private Config config = Config.empty();

        private Builder() {
        }

        @Override
        public PolicyValidator build() {
            return new PolicyValidator(this);
        }

        /**
         * Add an executor (that is not available as a java service).
         *
         * @param executor to evaluate policy statements
         * @return updated builder instance
         */
        public Builder addExecutor(PolicyExecutor executor) {
            this.executors.add(executor);
            return this;
        }

        /**
         * Update this builder from configuration.
         *
         * @param config configuration instance located on {@link PolicyValidatorService#configKey()}
         * @return updated builder instance
         */
        public Builder config(Config config) {
            this.config = config;
            config.get("validators").asList(Config.class).ifPresent(configs -> {
                for (Config validatorConfig : configs) {
                    validatorConfig.get("class").asString()
                            .ifPresentOrElse(clazz -> {
                                //attempt to instantiate
                                addExecutor(instantiate(clazz));
                            }, () -> {
                                throw new SecurityException(
                                        "validators key may only contain an array of class to class names, at key: "
                                                + validatorConfig.key());
                            });
                }
            });
            return this;
        }

        private PolicyExecutor instantiate(String className) {
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (Exception e) {
                throw new SecurityException("Failed to get class " + className, e);
            }

            try {
                return (PolicyExecutor) clazz.getConstructor().newInstance();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Could not instantiate: " + className + ". Class must have a default public");

                throw new SecurityException("Failed to load PolicyExecutor from class " + clazz, e);
            }
        }
    }

    /**
     * Configuration of policy validator - a statement and whether to inherit value
     * from parents.
     */
    public static final class PolicyConfig implements AbacValidatorConfig {
        private final List<String> policyStatements;
        private final boolean inherit;

        private PolicyConfig(Builder builder) {
            this.policyStatements = builder.policyStatements;
            this.inherit = builder.inherit;
        }

        /**
         * Creates a fluent API builder to build new instances of this class.
         *
         * @return a new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * The policy statements collected through configuration hierarchy.
         *
         * @return list of statements in order they should be executed
         */
        public List<String> policyStatements() {
            return Collections.unmodifiableList(policyStatements);
        }

        /**
         * Whether to inherit statements from parent configurations.
         *
         * @return true to inherit, false to execute only this class's statements
         */
        public boolean shouldInherit() {
            return inherit;
        }

        /**
         * A fluent API builder for {@link PolicyConfig}.
         */
        public static final class Builder implements io.helidon.common.Builder<PolicyConfig> {
            private final List<String> policyStatements = new LinkedList<>();
            private boolean inherit = true;

            private Builder() {
            }

            /**
             * The statement of this policy. Actual value depends on policy engine in use.
             *
             * @param policyStatement statement to validate access against
             * @return updated builder instance
             */
            public Builder statement(String policyStatement) {
                this.policyStatements.clear();
                this.policyStatements.add(policyStatement);
                return this;
            }

            /**
             * By default, all policy statements defined on all levels (application, class, method, field) are executed
             * in order. If set to false, on the last policy in the list is executed (e.g. only on method for resource method
             * of Jersey integration, if it is defined on method).
             * NOTE: if there is no statement defined on current level, we will use a statement from nearest level above
             *
             * @param inherit whether to inherit policies from annotations defined on higher level
             * @return updated builder instance
             */
            public Builder inherit(boolean inherit) {
                this.inherit = inherit;
                return this;
            }

            /**
             * Update this builder from configuration.
             *
             * @param config config instance located on the key {@link PolicyValidator#configKey()}
             * @return updated builder instance
             */
            public Builder config(Config config) {

                config.get("inherit").asBoolean().ifPresent(this::inherit);
                config.get("statement").asString().ifPresent(this::statement);

                return this;
            }

            Builder from(PolicyStatement annot) {
                return inherit(annot.inherit()).statement(annot.value());
            }

            Builder from(PolicyConfig config) {
                if (!config.inherit) {
                    policyStatements.clear();
                }
                inherit(config.inherit);
                policyStatements.addAll(config.policyStatements);
                return this;
            }

            @Override
            public PolicyConfig build() {
                return new PolicyConfig(this);
            }
        }
    }
}
