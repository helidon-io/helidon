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

package io.helidon.security.providers.abac;

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
import java.util.stream.Collectors;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import io.helidon.common.Errors;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.Config;
import io.helidon.security.AuthorizationResponse;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.SecurityLevel;
import io.helidon.security.SecurityResponse;
import io.helidon.security.providers.abac.spi.AbacValidator;
import io.helidon.security.providers.abac.spi.AbacValidatorService;
import io.helidon.security.spi.AuthorizationProvider;
import io.helidon.security.spi.SynchronousProvider;

/**
 * Attribute based access control (ABAC) provider.
 * This provider gathers all attributes to be validated on endpoint and makes sure they are all validated as expected during
 * authorization process.
 * Each attribute to be validated must have a {@link AbacValidator} implemented.
 *
 * @see #builder()
 * @see #create(Config)
 */
public final class AbacProvider extends SynchronousProvider implements AuthorizationProvider {
    private static final String CONFIG_KEY = "abac";

    private final List<AbacValidator<? extends AbacValidatorConfig>> validators = new ArrayList<>();
    private final Set<Class<? extends Annotation>> supportedAnnotations;
    private final Set<String> supportedConfigKeys;
    private final Set<Class<? extends AbacValidatorConfig>> supportedCustomObjects;
    private final boolean failOnUnvalidated;
    private final boolean failIfNoneValidated;

    private AbacProvider(Builder builder) {
        HelidonServiceLoader<AbacValidatorService> services =
                HelidonServiceLoader.create(ServiceLoader.load(AbacValidatorService.class));

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
    public static AbacProvider create(Config config) {
        return builder().config(config).build();
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

        EndpointConfig epConfig = providerRequest.endpointConfig();
        // list all "Attribute" annotations and make sure we support them
        validateAnnotations(epConfig, collector);
        // list all children of abac config and make sure one of the AbacValidators supports them
        validateConfig(epConfig, collector);
        // list all custom objects and check those that implement AttributeConfig and ...
        validateCustom(epConfig, collector);

        Optional<Config> abacConfig = epConfig.config(CONFIG_KEY);

        for (var validator : validators) {
            // order of preference - explicit class, configuration, annotation
            Class<? extends AbacValidatorConfig> configClass = validator.configClass();
            String configKey = validator.configKey();
            Collection<Class<? extends Annotation>> annotations = validator.supportedAnnotations();

            Optional<? extends AbacValidatorConfig> customObject = epConfig.instance(configClass);
            if (customObject.isPresent()) {
                attributes.add(new RuntimeAttribute(validator, customObject.get()));
            } else {
                abacConfig.map(it -> it.get(configKey)).ifPresentOrElse(
                        attribConfig -> {
                            attributes.add(new RuntimeAttribute(validator, validator.fromConfig(attribConfig)));
                        },
                        () -> {
                            List<Annotation> annotationConfig = new ArrayList<>();
                            for (SecurityLevel securityLevel : epConfig.securityLevels()) {
                                for (Class<? extends Annotation> annotation : annotations) {
                                    List<? extends Annotation> list = securityLevel
                                            .combineAnnotations(annotation,
                                                                EndpointConfig.AnnotationScope.values());
                                    annotationConfig.addAll(list);
                                }
                            }

                            if (!annotationConfig.isEmpty()) {
                                attributes.add(new RuntimeAttribute(validator,
                                                                    validator.fromAnnotations(epConfig)));
                            }
                        });
            }
        }

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
        epConfig.instanceKeys()
                .forEach(clazz -> {
                    int attributes = 0;
                    int unsupported = 0;
                    List<String> unsupportedClasses = new LinkedList<>();

                    if (AbacValidatorConfig.class.isInstance(epConfig.instance(clazz))) {
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
        config.config(CONFIG_KEY)
                .ifPresent(abacConfig -> validateAbacConfig(abacConfig, collector));
    }

    private void validateAbacConfig(Config abacConfig, Errors.Collector collector) {
        // we need to iterate first level subkeys to see if they are supported
        List<String> keys = abacConfig.asNodeList()
                .orElseGet(List::of)
                .stream()
                .map(Config::name)
                .collect(Collectors.toList());

        Set<String> uniqueKeys = new HashSet<>(keys);

        if (uniqueKeys.size() != keys.size()) {
            collector.fatal(keys, "There are duplicit keys under \"abac\" node in configuration.");
        }

        int attributes = 0;
        int unsupported = 0;
        List<String> unsupportedKeys = new LinkedList<>();

        for (String key : uniqueKeys) {
            attributes++;
            if (!supportedConfigKeys.contains(key)) {
                unsupported++;
                unsupportedKeys.add(key);
            }
        }

        //evaluate that we can continue
        boolean fail = false;
        if (unsupported != 0) {
            if ((unsupported == attributes) && failIfNoneValidated) {
                fail = true;
            } else if (failOnUnvalidated) {
                fail = true;
            }

            if (fail) {
                for (String key : unsupportedKeys) {
                    collector.fatal(this,
                                    "\"" + key + "\" ABAC attribute config key is not supported.");
                }
                collector.fatal(this, "Supported ABAC config keys: " + supportedConfigKeys);
            }
        }
    }

    private void validateAnnotations(EndpointConfig epConfig, Errors.Collector collector) {
        // list all annotations that are marked as Attribute and make sure some AbacValidator supports them

        for (SecurityLevel securityLevel : epConfig.securityLevels()) {
            int attributeAnnotations = 0;
            int unsupported = 0;
            List<String> unsupportedClassNames = new LinkedList<>();
            Map<Class<? extends Annotation>, List<Annotation>> allAnnotations = securityLevel.allAnnotations();

            for (Class<? extends Annotation> type : allAnnotations.keySet()) {
                AbacAnnotation abacAnnotation = type.getAnnotation(AbacAnnotation.class);
                if (null != abacAnnotation || isSupportedAnnotation(type)) {
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
    }

    private boolean isSupportedAnnotation(Class<? extends Annotation> type) {
        return RolesAllowed.class.equals(type)
                || PermitAll.class.equals(type)
                || DenyAll.class.equals(type);
    }

    /**
     * A fluent API builder for {@link AbacProvider}.
     */
    public static final class Builder implements io.helidon.common.Builder<AbacProvider> {
        private final List<AbacValidator<? extends AbacValidatorConfig>> validators = new ArrayList<>();
        private Config config = Config.empty();
        private boolean failOnUnvalidated = true;
        private boolean failIfNoneValidated = true;

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
         * Configuration to use for validator instances.
         * This builder is NOT updated from the provided config, use {@link #config(Config)} to update this builder.
         *
         * @param config configuration
         * @return updated builder instance
         */
        public Builder configuration(Config config) {
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
         * Update builder from configuration and set the config to {@link #configuration(io.helidon.config.Config)}.
         *
         * @param config configuration placed on the key of this provider
         * @return updated builder instance
         */
        public Builder config(Config config) {
            configuration(config);

            config.get("fail-on-unvalidated").asBoolean().ifPresent(this::failOnUnvalidated);
            config.get("fail-if-none-validated").asBoolean().ifPresent(this::failIfNoneValidated);

            return this;
        }
    }
}
