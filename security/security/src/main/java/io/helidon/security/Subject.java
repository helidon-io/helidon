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

package io.helidon.security;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.security.util.AbacSupport;

/**
 * A security subject, representing a user or a service.
 */
public final class Subject implements AbacSupport {
    private final List<Grant> grants = new LinkedList<>();
    private final List<Principal> principals = new LinkedList<>();
    private final Map<String, List<Grant>> grantsByType = new HashMap<>();

    private final AbacSupport attributes;
    private final Principal principal;

    private final ClassToInstanceStore<Object> privateCredentials = new ClassToInstanceStore<>();
    private final ClassToInstanceStore<Object> publicCredentials = new ClassToInstanceStore<>();

    private Subject(Builder builder) {
        BasicAttributes properties = BasicAttributes.create(builder.properties);
        this.principal = builder.principal;
        this.principals.addAll(builder.principals);

        builder.grants.forEach(grant -> {
            grants.add(grant);
            grantsByType.computeIfAbsent(grant.type(), key -> new LinkedList<>()).add(grant);
        });

        properties.put("principal", principal);
        properties.put("grant", grants);

        // for each grant type, add grant list
        grantsByType.forEach(properties::put);

        this.attributes = properties;
        this.privateCredentials.putAll(builder.privateCredentials);
        this.publicCredentials.putAll(builder.publicCredentials);
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
     * Create a new subject for a principal.
     * If you want to configure additional details ({@link Grant Grants}, public and/or private credentials, additional
     * {@link Principal Principals}), please use fluent API {@link #builder()}.
     *
     * @param principal principal this subject represents
     * @return a new subject instance with the single principal
     */
    public static Subject create(Principal principal) {
        return builder().principal(principal).build();
    }

    /**
     * Get the principal this subject is created for (e.g. the "main" principal of this subject).
     *
     * @return principal
     */
    public Principal principal() {
        return principal;
    }

    /**
     * Get all principals of this subject (including the one returned by {@link #principal()}).
     *
     * @return all principals of this subject
     */
    public List<Principal> principals() {
        return Collections.unmodifiableList(principals);
    }

    /**
     * Get all grants of a specific type determined by type's class.
     *
     * @param grantType type of grant (e.g. {@link Role Role.class})
     * @param <T>       type of the grant's type (e.g. {@link Role Role}
     * @return list of grants of the specific type associated with this subject (may be empty)
     */
    public <T extends Grant> List<T> grants(Class<T> grantType) {
        return grants.stream()
                .filter(grantType::isInstance)
                .map(grantType::cast)
                .collect(Collectors.toList());
    }

    /**
     * Get all grants of a specific type determined by type's name.
     *
     * @param grantType type of grant (e.g. "role" or "scope")
     * @return list of grants of the specific type associated with this subject (may be empty)
     */
    public List<Grant> grantsByType(String grantType) {
        return Collections.unmodifiableList(grantsByType.getOrDefault(grantType, List.of()));
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
     * Get public credential for the specified type.
     *
     * @param credential credential type's class
     * @param <T>        credential type
     * @return optional of public credential of the type defined
     */
    public <T> Optional<T> publicCredential(Class<T> credential) {
        return publicCredentials.getInstance(credential);
    }

    /**
     * Get private credential for the specified type.
     *
     * @param credential credential type's class
     * @param <T>        credential type
     * @return optional of private credential of the type defined
     */
    public <T> Optional<T> privateCredential(Class<T> credential) {
        return privateCredentials.getInstance(credential);
    }

    /**
     * Create a java {@link javax.security.auth.Subject} from this subject.
     *
     * @return an instance of Subject
     */
    public javax.security.auth.Subject toJavaSubject() {
        Set<java.security.Principal> principals = new LinkedHashSet<>(this.principals);

        for (String key : attributes.abacAttributeNames()) {
            attributes.abacAttribute(key)
                    .stream()
                    .filter(prop -> prop instanceof Principal)
                    .map(Principal.class::cast)
                    .forEach(principals::add);
        }

        principals.addAll(grants);

        Set<Object> pubCredentials = new HashSet<>(publicCredentials.values());

        Set<Object> privCredentials = new HashSet<>(privateCredentials.values());

        return new javax.security.auth.Subject(
                true,
                principals,
                pubCredentials,
                privCredentials
        );
    }

    /**
     * Will add all principals and credentials from another subject to this subject, will not replace {@link #principals()}.
     *
     * @param another the other subject to combine with this subject
     * @return a new subject that is a combination of this subject and the other subject, this subject is more significant
     */
    public Subject combine(Subject another) {
        Builder builder = Subject.builder()
                .addPrincipal(this.principal);

        // add this subject
        principals.forEach(builder::addPrincipal);
        privateCredentials.keys().forEach(key -> builder.addPrivateCredential(key, privateCredentials.getInstance(key)));
        publicCredentials.keys().forEach(key -> builder.addPublicCredential(key, publicCredentials.getInstance(key)));
        grants.forEach(builder::addGrant);
        attributes.abacAttributeNames().forEach(key -> builder.addAttribute(key, attributes.abacAttribute(key)));

        // add the other subject
        another.principals.forEach(builder::addPrincipal);
        another.privateCredentials.keys()
                .forEach(key -> builder.addPrivateCredential(key, another.privateCredentials.getInstance(key)));
        another.publicCredentials.keys()
                .forEach(key -> builder.addPublicCredential(key, another.publicCredentials.getInstance(key)));
        another.grants.forEach(builder::addGrant);
        another.attributes.abacAttributeNames().forEach(key -> builder.addAttribute(key, another.attributes.abacAttribute(key)));

        return builder.build();
    }

    @Override
    public String toString() {
        return toJavaSubject().toString();
    }

    /**
     * A fluent API builder for {@link Subject}.
     */
    public static final class Builder implements io.helidon.common.Builder<Subject> {
        private final List<Grant> grants = new LinkedList<>();
        private final List<Principal> principals = new LinkedList<>();
        private final ClassToInstanceStore<Object> privateCredentials = new ClassToInstanceStore<>();
        private final ClassToInstanceStore<Object> publicCredentials = new ClassToInstanceStore<>();
        private BasicAttributes properties = BasicAttributes.create();
        private Principal principal;

        private Builder() {
        }

        @Override
        public Subject build() {
            return new Subject(this);
        }

        /**
         * Update this builder with all security information from the
         * subject provided.
         *
         * @param subject subject to copy information from
         * @return updated builder instance
         */
        public Builder update(Subject subject) {
            principal = subject.principal;
            grants.addAll(subject.grants);
            principals.addAll(subject.principals);
            privateCredentials.putAll(subject.privateCredentials);
            publicCredentials.putAll(subject.publicCredentials);
            for (String name : subject.attributes.abacAttributeNames()) {
                subject.attributes.abacAttribute(name).ifPresent(attrib -> properties.put(name, attrib));
            }
            return this;
        }

        /**
         * Main principal of this subject.
         *
         * @param principal principal (e.g. a user or a service)
         * @return updated builder instance
         */
        public Builder principal(Principal principal) {
            this.principal = principal;
            this.principals.add(principal);
            return this;
        }

        /**
         * Add a grant to this subject.
         *
         * @param grant grant to add (e.g. a role, scope, permission etc.)
         * @return updated builder instance
         */
        public Builder addGrant(Grant grant) {
            this.grants.add(grant);
            return this;
        }

        /**
         * Add a public credential to this subject.
         * Only one instance of a type may be added to a subject.
         *
         * @param className class of the credential (e.g. X509 certificate)
         * @param instance  instance of the credential
         * @return updated builder instance
         */
        public Builder addPublicCredential(Class<?> className, Object instance) {
            publicCredentials.putInstance(className, instance);
            return this;
        }

        /**
         * Add a public credential to this subject to be bound under its class.
         *
         * @param instance instance of the credential, the class it will be bound to is obtained through {@link Object#getClass()
         *                 instance.getClass()}
         * @return updated builder instance
         */
        public Builder addPublicCredential(Object instance) {
            publicCredentials.putInstance(instance);
            return this;
        }

        /**
         * Add a private credential to this subject.
         * Only one instance of a type may be added to a subject.
         *
         * @param className class of the credential (e.g. X509 certificate)
         * @param instance  instance of the credential
         * @return updated builder instance
         */
        public Builder addPrivateCredential(Class<?> className, Object instance) {
            privateCredentials.putInstance(className, instance);
            return this;
        }

        /**
         * Add a private credential to this subject to be bound under its class.
         *
         * @param instance instance of the credential, the class it will be bound to is obtained through {@link Object#getClass()
         *                 instance.getClass()}
         * @return updated builder instance
         */
        public Builder addPrivateCredential(Object instance) {
            privateCredentials.putInstance(instance);
            return this;
        }

        /**
         * Add all attributes to this subject. Attributes can extend information with data not fitting to other fields of this
         * subject.
         *
         * @param attributes attributes with key/value pairs
         * @return updated builder instance
         */
        public Builder attributes(AbacSupport attributes) {
            this.properties = BasicAttributes.create(attributes);
            return this;
        }

        /**
         * Add an attribute to this subject.
         *
         * @param key   name of the attribute
         * @param value value of the attribute
         * @return updated builder instance
         */
        public Builder addAttribute(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        /**
         * Add a principal to the list of principals of this subject.
         * If {@link #principal(Principal)} was not invoked prior to this method, it will also set the "main" principal.
         *
         * @param principal principal to add to this subject
         * @return updated builder instance
         */
        public Builder addPrincipal(Principal principal) {
            if (null == this.principal) {
                this.principal = principal;
            }

            this.principals.add(principal);

            return this;
        }
    }
}
