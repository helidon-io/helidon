/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.CollectionsHelper;
import io.helidon.config.Config;
import io.helidon.security.spi.SecurityProvider;
import io.helidon.security.util.AbacSupport;

/**
 * Each endpoint can have security configuration either statically declared (e.g. annotations) or
 * dynamically provided through manual security request.
 * This class covers all kinds of configurations supported:
 * <ul>
 * <li>Annotations as gathered by the integration component (e.g. from Jersey resource class and method)</li>
 * <li>Configuration as read from a configuration resource (e.g. when integrating web server)</li>
 * <li>Attributes (key/value) configured by a programmer</li>
 * <li>Custom objects configured by a programmer</li>
 * </ul>
 *
 * Each provider defines annotations, configuration keys, attributes and custom objects it supports and expects.
 *
 * @see SecurityProvider#supportedAnnotations
 * @see SecurityProvider#supportedCustomObjects
 * @see SecurityProvider#supportedConfigKeys
 * @see SecurityProvider#supportedAttributes
 */
public class EndpointConfig implements AbacSupport {
    private final List<SecurityLevel> securityLevels;
    private final AbacSupport attributes;
    private final ClassToInstanceStore<Object> customObjects;
    private final Map<String, Config> configMap;

    private EndpointConfig(Builder builder) {
        this.securityLevels = Collections.unmodifiableList(builder.securityLevels);
        this.attributes = BasicAttributes.create(builder.attributes);
        this.customObjects = new ClassToInstanceStore<>();
        this.customObjects.putAll(builder.customObjects);
        this.configMap = new HashMap<>(builder.configMap);
    }

    /**
     * Creates a fluent API builder to build new instances of this class.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an instance of endpoint config with default values (e.g. all empty).
     *
     * @return endpoint config instance
     */
    public static EndpointConfig create() {
        return builder().build();
    }

    @Override
    public Object abacAttributeRaw(String key) {
        return attributes.abacAttributeRaw(key);
    }

    @Override
    public Collection<String> abacAttributeNames() {
        return attributes.abacAttributeNames();
    }

    /**
     * Get an instance of a custom object configuring this endpoint.
     *
     * @param clazz class the instance is bound under (only explicit binding is supported)
     * @param <U>   type of the configuration
     * @return instance of the custom object if present
     */
    public <U> Optional<U> instance(Class<U> clazz) {
        return customObjects.getInstance(clazz);
    }

    /**
     * Get all classes of custom endpoint configuration object registered.
     *
     * @return classes that are keys in the custom object store
     */
    public Collection<Class<?>> instanceKeys() {
        return customObjects.keys();
    }

    /**
     * Get {@link Config} instance for a config key.
     *
     * @param configKey key of configuration expected
     * @return Config instance if present in this endpoint configuration
     */
    public Optional<Config> config(String configKey) {
        return Optional.ofNullable(configMap.get(configKey));
    }

    /**
     * All custom annotations for scopes defined in parameters, in the same order.
     *
     * @param scopes scopes the caller is interested in
     * @return a map of annotation classes to annotation instances
     * @see SecurityProvider#supportedAnnotations()
     * @deprecated use iteration over security levels instead
     */
    @Deprecated
    public Map<Class<? extends Annotation>, List<Annotation>> annotations(AnnotationScope... scopes) {
        Map<Class<? extends Annotation>, List<Annotation>> result = new HashMap<>();

        for (AnnotationScope scope : scopes) {
            Map<Class<? extends Annotation>, List<Annotation>> map;
            switch (scope) {
            case APPLICATION:
                map = securityLevels.get(0).getClassLevelAnnotations();
                break;
            case CLASS:
                map = securityLevels.get(securityLevels.size() - 1).getClassLevelAnnotations();
                break;
            case METHOD:
                map = securityLevels.get(securityLevels.size() - 1).getMethodLevelAnnotations();
                break;
            default:
                map = null;
            }
            if (null != map) {
                map.forEach((annotClass, annotList) -> result.computeIfAbsent(annotClass, aClass -> new LinkedList<>())
                        .addAll(annotList));
            }
        }

        return result;
    }

    /**
     * Get all security levels endpoint configuration object registered.
     * The first level represents {@link AnnotationScope#APPLICATION} level annotations.
     * Other levels are representations of each resource and method used on path to get to the target method.
     *
     * @return all registered security levels
     */
    public List<SecurityLevel> securityLevels() {
        return securityLevels;
    }

    /**
     * Get all annotations of a specific class declared on any level.
     *
     * @param annotationClass Class of annotation you want
     * @param scopes          scopes the caller is interested in
     * @param <T>             type of annotation wanted
     * @return list of annotations in order specified by methodFirst parameter
     * @deprecated use iteration over security levels instead
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T extends Annotation> List<T> combineAnnotations(Class<T> annotationClass, AnnotationScope... scopes) {
        List<T> result = new LinkedList<>();

        result.addAll((Collection<? extends T>) annotations(scopes).getOrDefault(annotationClass, CollectionsHelper.listOf()));

        return result;
    }

    /**
     * Derive a new endpoint configuration builder based on this instance.
     *
     * @return builder to build a modified copy of this endpoint config
     */
    public Builder derive() {
        Builder result = builder()
                .attributes(attributes)
                .customObjects(customObjects)
                .configMap(configMap)
                .securityLevels(securityLevels);

        return result;
    }

    /**
     * Scope of annotations used in applications that integrate
     * security.
     */
    public enum AnnotationScope {
        /**
         * Annotations on an application class or application layer.
         * Example: JAX-RS application class annotation
         */
        APPLICATION,
        /**
         * Annotations on a resource class.
         * Example: JAX-RS resource class annotation
         */
        CLASS,
        /**
         * Annotation on a resource method.
         * Example: JAX-RS resource method annotation
         */
        METHOD,
        /**
         * Annotations on field.
         * Example: annotated client declaration
         */
        FIELD
    }

    /**
     * A fluent API builder for {@link EndpointConfig}.
     */
    public static final class Builder implements io.helidon.common.Builder<EndpointConfig> {
        private final ClassToInstanceStore<Object> customObjects = new ClassToInstanceStore<>();
        private final Map<String, Config> configMap = new HashMap<>();
        private final List<SecurityLevel> securityLevels = new ArrayList<>();
        private BasicAttributes attributes = BasicAttributes.create();

        private Builder() {
        }

        @Override
        public EndpointConfig build() {
            return new EndpointConfig(this);
        }

        /**
         * Set or replace a custom object. This object will be provided to security provider.
         * Objects are stored by class, so we can have multiple objects of different classes
         * (e.g. when using multiple authorizers/authenticators). Class of object is defined by security provider.
         *
         * @param objectClass Class of object as expected by security provider
         * @param anObject    Custom object to propagate to security provider
         * @param <U>         Type of the custom object to be stored. The object instance is available ONLY under this class
         * @param <V>         Type of instance (must be descendant of U)
         * @return updated Builder instance
         */
        public <U, V extends U> Builder customObject(Class<U> objectClass, V anObject) {
            this.customObjects.putInstance(objectClass, anObject);
            return this;
        }

        /**
         * Provide custom object map to be sent to security providers.
         *
         * @param customObjects Class to its instance map of custom objects
         * @return Updated builder instance
         * @see #customObject(Class, Object)
         */
        public Builder customObjects(ClassToInstanceStore<Object> customObjects) {
            this.customObjects.putAll(customObjects);
            return this;
        }

        /**
         * Provide a configuration for provider to use. This allows a provider to define a custom configuration key.
         *
         * @param configKey     key this configuration is stored under
         * @param configuration {@link Config configuration} stored under the key, as expected by security provider
         * @return Updated builder instance
         */
        public Builder config(String configKey, Config configuration) {
            this.configMap.put(configKey, configuration);
            return this;
        }

        /**
         * Provider a map of cofiguration keys to configurations for provider(s) to use.
         *
         * @param configMap map of configurations
         * @return updated builder instance
         */
        public Builder configMap(Map<String, Config> configMap) {
            this.configMap.putAll(configMap);
            return this;
        }

        /**
         * Add annotations of a specific scope to this request builder.
         * Only used by frameworks that use annotations.
         *
         * @param scope       Annotation scope to add annotations for
         * @param annotations Collected annotations based on security provider requirements.
         * @return updated Builder instance
         * @deprecated Use the {@link #securityLevels(List) securityLevels} method.
         */
        @Deprecated
        public Builder annotations(AnnotationScope scope,
                                   Map<Class<? extends Annotation>, List<Annotation>> annotations) {
            // here we must switch from a proxy to actual annotation type
            Map<Class<? extends Annotation>, List<Annotation>> newAnnots = new HashMap<>();

            if (securityLevels.isEmpty()) {
                securityLevels.add(SecurityLevel.create("APPLICATION").build()); //Security level of Application
                securityLevels.add(SecurityLevel.create("CLASS").build()); // Security level of class and method
            }
            SecurityLevel securityLevel;
            int index;
            if (scope == AnnotationScope.APPLICATION) {
                index = 0;
            } else {
                index = securityLevels.size() - 1;
            }
            securityLevel = securityLevels.get(index);

            annotations.forEach((aClass, list) -> {
                if (!list.isEmpty()) {
                    Annotation annotation = list.get(0);
                    newAnnots.put(annotation.annotationType(), list);
                }
            });

            switch (scope) {
            case APPLICATION:
            case CLASS:
                securityLevels.set(index,
                                   SecurityLevel.create(securityLevel)
                                           .withClassAnnotations(newAnnots)
                                           .build());
                break;
            case METHOD:
                securityLevels.set(index,
                                   SecurityLevel.create(securityLevel)
                                           .withMethodAnnotations(newAnnots)
                                           .build());
                break;
            default:
                throw new IllegalStateException("Scope FIELD is not supported here.");
            }

            return this;
        }

        /**
         * Attributes of this endpoint configuration.
         *
         * @param attributes attributes to set for this builder
         * @return updated builder instance
         */
        private Builder attributes(AbacSupport attributes) {
            this.attributes = BasicAttributes.create(attributes);
            return this;
        }

        /**
         * Add an attribute to this endpoint configuration builder.
         *
         * @param key   name of the attribute as expected by the security provider
         * @param value value of this attribute
         * @return updated builder instance
         */
        public Builder addAtribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        /**
         * Sets security levels to this endpoint configuration builder.
         *
         * @param securityLevels list of security levels
         * @return updated builder instance
         */
        public Builder securityLevels(List<SecurityLevel> securityLevels) {
            this.securityLevels.addAll(securityLevels);
            return this;
        }
    }
}
