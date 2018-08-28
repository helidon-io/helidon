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

package io.helidon.security.abac;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.security.RolesAllowed;

import io.helidon.common.Errors;
import io.helidon.common.OptionalHelper;
import io.helidon.config.Config;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityResponse;
import io.helidon.security.abac.spi.AbacValidator;
import io.helidon.security.abac.spi.AbacValidatorService;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Attribute based access control (ABAC) provider.
 * This provider gathers all attributes to be validated on endpoint and makes sure they are all validated as expected during
 * authorization process.
 * Each attribute to be validated must have a {@link AbacValidator} implemented.
 *
 * @see #builder()
 * @see #from(Config)
 */
public class AbacProvider extends SynchronousProvider implements AuthorizationProvider {
    private final List<AbacValidator<? extends AbacValidatorConfig>> validators = new ArrayList<>();
    private final Set<Class<? extends Annotation>> supportedAnnotations;
    private final Set<String> supportedConfigKeys;
    private final Set<Class<? extends AbacValidatorConfig>> supportedCustomObjects;
    private final boolean failOnUnvalidated;
    private final boolean failIfNoneValidated;

    private AbacProvider(Builder builder) {
        ServiceLoader<AbacValidatorService> services = ServiceLoader.load(AbacValidatorService.class);

        for (AbacValidatorService service : services) {
            validators.add(service.instantiate(builder.config.get(service.configKey())));
        }

        this.validators.addAll(builder.validators);

        Set<Class<? extends Annotation>> annotations = new HashSet<>();
        Set<String> configKeys = new HashSet<>();
        Set<Class<? extends AbacValidatorConfig>> customObjects = new HashSet<>();

        validators.forEach(v -> {
            annotations.addAll(v.supportedAnnotations());
            configKeys.add(v.configKey());
            customObjects.add(v.configClass());
        });

        this.supportedAnnotations = Collections.unmodifiableSet(annotations);
        this.supportedConfigKeys = Collections.unmodifiableSet(configKeys);
        this.supportedCustomObjects = Collections.unmodifiableSet(customObjects);
        this.failOnUnvalidated = builder.failOnUnvalidated;
        this.failIfNoneValidated = builder.failIfNoneValidated;
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
     * Creates a new provider instance from configuration.
     *
     * @param config configuration
     * @return ABAC provider instantiated from config
     */
    public static AbacProvider from(Config config) {
        return builder().from(config).build();
    }

    /**
     * Creates a new provider instance with default configuration.
     *
     * @return ABAC provider
     */
    public static AbacProvider create() {
        return builder().build();
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        return supportedAnnotations;
    }

    @Override
    protected AuthorizationResponse syncAuthorize(ProviderRequest providerRequest) {
        //let's find attributes to be validated
        Errors.Collector collector = Errors.collector();
        List<RuntimeAttribute> attributes = new ArrayList<>();

        EndpointConfig epConfig = providerRequest.getEndpointConfig();
        // list all "Attribute" annotations and make sure we support them
        validateAnnotations(epConfig, collector);
        // list all children of abac config and make sure one of the AbacValidators supports them
        validateConfig(epConfig, collector);
        // list all custom objects and check those that implement AttributeConfig and ...
        validateCustom(epConfig, collector);

        validators.forEach(validator -> {
            // order of preference - explicit class, configuration, annotation
            Class<? extends AbacValidatorConfig> configClass = validator.configClass();
            String configKey = validator.configKey();
            Collection<Class<? extends Annotation>> annotations = validator.supportedAnnotations();

            Optional<? extends AbacValidatorConfig> customObject = epConfig.getInstance(configClass);
            if (customObject.isPresent()) {
                attributes.add(new RuntimeAttribute(validator, customObject.get()));
            } else {
                OptionalHelper.from(epConfig.getConfig(configKey))
                        .ifPresentOrElse(attribConfig -> attributes
                                .add(new RuntimeAttribute(validator, validator.fromConfig(attribConfig))), () -> {
                            List<Annotation> annotationConfig = new ArrayList<>();
                            for (Class<? extends Annotation> annotation : annotations) {
                                List<? extends Annotation> list = epConfig
                                        .combineAnnotations(annotation, EndpointConfig.AnnotationScope.values());
                                annotationConfig.addAll(list);
                            }

                            if (!annotationConfig.isEmpty()) {
                                attributes.add(new RuntimeAttribute(validator, validator.fromAnnotations(annotationConfig)));
                            }
                        });
            }
        });

        for (RuntimeAttribute attribute : attributes) {
            validate(attribute.getValidator(), attribute.getConfig(), collector, providerRequest);
        }

        Errors errors = collector.collect();

        if (errors.isValid()) {
            return AuthorizationResponse.permit();
        }

        return AuthorizationResponse.builder()
                .status(SecurityResponse.SecurityStatus.FAILURE)
                .description(errors.toString())
                .build();
    }

    @SuppressWarnings("unchecked")
    private <A extends AbacValidator<B>, B extends AbacValidatorConfig> void validate(A validator,
                                                                                      AbacValidatorConfig config,
                                                                                      Errors.Collector collector,
                                                                                      ProviderRequest context) {
        validator.validate((B) config, collector, context);
    }

    private void validateCustom(EndpointConfig epConfig, Errors.Collector collector) {
        epConfig.getInstanceKeys()
                .forEach(clazz -> {
                    int attributes = 0;
                    int unsupported = 0;
                    List<String> unsupportedClasses = new LinkedList<>();

                    if (AbacValidatorConfig.class.isInstance(epConfig.getInstance(clazz))) {
                        attributes++;
                        if (!supportedCustomObjects.contains(clazz)) {
                            unsupported++;
                            unsupportedClasses.add(clazz.getName());
                        }
                    }

                    //evaluate that we can continue
                    boolean fail = false;
                    if (unsupported != 0) {
                        if (unsupported == attributes && failIfNoneValidated) {
                            fail = true;
                        } else if (failOnUnvalidated) {
                            fail = true;
                        }

                        if (fail) {
                            for (String key : unsupportedClasses) {
                                collector.fatal(this,
                                                key + " custom object is not supported.");
                            }
                            collector.fatal(this, "Supported custom objects: " + supportedCustomObjects);
                        }
                    }
                });
    }

    private void validateConfig(EndpointConfig config, Errors.Collector collector) {
        config.getConfig("abac")
                .ifPresent(abacConfig -> abacConfig.asOptionalMap()
                        .ifPresent(theMap -> {
                            int attributes = 0;
                            int unsupported = 0;
                            List<String> unsupportedKeys = new LinkedList<>();

                            for (String key : theMap.keySet()) {
                                attributes++;
                                if (!supportedConfigKeys.contains(key)) {
                                    unsupported++;
                                    unsupportedKeys.add(key);
                                }
                            }

                            //evaluate that we can continue
                            boolean fail = false;
                            if (unsupported != 0) {
                                if (unsupported == attributes && failIfNoneValidated) {
                                    fail = true;
                                } else if (failOnUnvalidated) {
                                    fail = true;
                                }

                                if (fail) {
                                    for (String key : unsupportedKeys) {
                                        collector.fatal(this,
                                                        key + " attribute config key is not supported.");
                                    }
                                    collector.fatal(this, "Supported config keys: " + supportedConfigKeys);
                                }
                            }
                        }));
    }

    private void validateAnnotations(EndpointConfig epConfig, Errors.Collector collector) {
        // list all annotations that are marked as Attribute and make sure some AbacValidator supports them
        Map<Class<? extends Annotation>, List<Annotation>> allAnnotations = epConfig
                .getAnnotations(EndpointConfig.AnnotationScope.values());

        int attributeAnnotations = 0;
        int unsupported = 0;
        List<String> unsupportedClassNames = new LinkedList<>();

        for (Class<? extends Annotation> type : allAnnotations.keySet()) {
            AbacAnnotation abacAnnotation = type.getAnnotation(AbacAnnotation.class);
            if (null != abacAnnotation || RolesAllowed.class.equals(type)) {
                attributeAnnotations++;
                if (!supportedAnnotations.contains(type)) {
                    unsupported++;
                    unsupportedClassNames.add(type.getName());
                }
            }
        }

        //evaluate that we can continue
        if (unsupported != 0) {
            boolean fail = failOnUnvalidated;

            if (unsupported == attributeAnnotations && failIfNoneValidated) {
                fail = true;
            }

            if (fail) {
                for (String unsupportedClassName : unsupportedClassNames) {
                    collector.fatal(this,
                                    unsupportedClassName + " attribute annotation is not supported.");
                }
                collector.fatal(this, "Supported annotations: " + supportedAnnotations);
            }
        }
    }

    /**
     * A fluent API builder for {@link AbacProvider}.
     */
    public static class Builder implements io.helidon.common.Builder<AbacProvider> {
        private Config config = Config.empty();
        private boolean failOnUnvalidated = true;
        private boolean failIfNoneValidated = true;
        private final List<AbacValidator<? extends AbacValidatorConfig>> validators = new ArrayList<>();

        private Builder() {
        }

        @Override
        public AbacProvider build() {
            return new AbacProvider(this);
        }

        /**
         * Add an explicit (e.g. not configurable automatically from a java service) attribute validator.
         *
         * @param validator validator to add
         * @return updated builder instance
         * @see AbacValidatorService
         */
        public Builder addValidator(AbacValidator<? extends AbacValidatorConfig> validator) {
            this.validators.add(validator);
            return this;
        }

        /**
         * Configuration to use for validator instances (by default this is the provider's configuration that would be sent
         * to {@link #from(Config)}.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        /**
         * Whether to fail if any attribute is left unvalidated.
         *
         * @param failOnUnvalidated true for failure on unvalidated, false if it is OK to fail some of the validations
         * @return updated builder instance
         */
        public Builder failOnUnvalidated(boolean failOnUnvalidated) {
            this.failOnUnvalidated = failOnUnvalidated;
            return this;
        }

        /**
         * Whether to fail if NONE of the attributes is validated.
         *
         * @param failIfNoneValidated true for failure on unvalidated, false if it is OK not to validate any attribute
         * @return updated builder instance
         */
        public Builder failIfNoneValidated(boolean failIfNoneValidated) {
            this.failIfNoneValidated = failIfNoneValidated;
            return this;
        }

        /**
         * Update builder from configuration.
         *
         * @param config configuration placed on the key of this provider
         * @return updated builder instance
         */
        public Builder from(Config config) {
            Builder b = builder().config(config);

            config.get("fail-on-unvalidated").asOptionalBoolean().ifPresent(b::failOnUnvalidated);
            config.get("fail-if-none-validated").asOptionalBoolean().ifPresent(b::failIfNoneValidated);

            return b;
        }
    }
}
