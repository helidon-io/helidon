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

package io.helidon.security.abac.role;

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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityLevel;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.abac.AbacAnnotation;
import io.helidon.security.providers.abac.AbacValidatorConfig;
import io.helidon.security.providers.abac.spi.AbacValidator;

/**
 * Validator capable of validating role attributes of a subject.
 * In default configuration, checks roles of current user's subject. This can be overridden to support user and service, or just
 * a service either on global level (see {@link RoleValidatorService#configKey()} and {@link #configKey()}.
 *
 * <p>
 * This validator supports both {@link RolesAllowed} and {@link Roles} annotations.
 */
public final class RoleValidator implements AbacValidator<RoleValidator.RoleConfig> {
    private RoleValidator() {
    }

    /**
     * Create a new instance of role validator.
     *
     * @return a new instance with default configuration
     */
    public static RoleValidator create() {
        return new RoleValidator();
    }

    @Override
    public Class<RoleConfig> configClass() {
        return RoleConfig.class;
    }

    @Override
    public String configKey() {
        return "roles-allowed";
    }

    @Override
    public RoleConfig fromConfig(Config config) {
        return RoleConfig.create(config);
    }

    @Override
    public RoleConfig fromAnnotations(EndpointConfig endpointConfig) {
        RoleConfig.Builder builder = RoleConfig.builder();

        for (SecurityLevel securityLevel : endpointConfig.securityLevels()) {
            for (EndpointConfig.AnnotationScope scope : EndpointConfig.AnnotationScope.values()) {
                //Order of the annotations matters because of annotation handling.
                List<Annotation> annotations = new ArrayList<>();
                for (Class<? extends Annotation> annotation : supportedAnnotations()) {
                    annotations.addAll(securityLevel.filterAnnotations(annotation, scope));
                }
                List<String> roles = new ArrayList<>();
                List<String> serviceRoles = new ArrayList<>();
                for (Annotation annotation : annotations) {
                    if (annotation instanceof RolesAllowed) {
                        // these are user roles by default
                        roles.addAll(Arrays.asList(((RolesAllowed) annotation).value()));
                        builder.permitAll(false);
                        builder.denyAll(false);
                    } else if (annotation instanceof Roles) {
                        // these are configured
                        Roles r = (Roles) annotation;
                        if (r.subjectType() == SubjectType.USER) {
                            roles.addAll(Arrays.asList(r.value()));
                        } else {
                            serviceRoles.addAll(Arrays.asList(r.value()));
                        }
                        builder.permitAll(false);
                        builder.denyAll(false);
                    } else if (annotation instanceof PermitAll) {
                        builder.permitAll(true);
                        builder.denyAll(false);
                    } else if (annotation instanceof DenyAll) {
                        builder.permitAll(false);
                        builder.denyAll(true);
                    }
                }
                if (!roles.isEmpty()) {
                    builder.clearRoles().addRoles(roles);
                }
                if (!serviceRoles.isEmpty()) {
                    builder.clearServiceRoles().addServiceRoles(serviceRoles);
                }
            }
        }
        return builder.build();
    }

    @Override
    public void validate(RoleConfig config, Errors.Collector collector, ProviderRequest request) {
        if (config.denyAll()) {
            collector.fatal(this, "Access denied by DenyAll.");
            return;
        }
        if (config.permitAll()) {
            return;
        }
        validate(config.userRolesAllowed(), collector, request.subject(), SubjectType.USER);
        validate(config.serviceRolesAllowed(), collector, request.service(), SubjectType.SERVICE);
    }

    private void validate(Set<String> rolesAllowed, Errors.Collector collector, Optional<Subject> subject, SubjectType type) {
        if (rolesAllowed.isEmpty()) {
            // no required roles
            return;
        }

        Set<String> roleGrants = subject
                .map(sub -> sub.grants(Role.class))
                .orElse(List.of())
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        boolean notFound = true;

        for (String role : rolesAllowed) {
            if (roleGrants.contains(role)) {
                notFound = false;
                break;
            }
        }
        if (notFound) {
            collector.fatal(this, type + " is not in required roles: " + rolesAllowed + ", only in: " + roleGrants);
        }
    }

    @Override
    public Collection<Class<? extends Annotation>> supportedAnnotations() {
        //Order of the annotations matters because of annotation handling.
        return List.of(RolesAllowed.class, Roles.class, RolesContainer.class, PermitAll.class, DenyAll.class);
    }

    /**
     * A definition of "roles allowed" for a specific subject type.
     * If user/service is in any of the roles, access will be granted.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @Repeatable(RolesContainer.class)
    @AbacAnnotation
    public @interface Roles {
        /**
         * Array of roles allowed for this resource.
         *
         * @return roles allowed
         */
        String[] value();

        /**
         * Subject type for this restriction, defaults to {@link SubjectType#USER}.
         *
         * @return subject type
         */
        SubjectType subjectType() default SubjectType.USER;
    }

    /**
     * Repeatable annotation for {@link Roles}.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @Documented
    @Inherited
    @AbacAnnotation
    public @interface RolesContainer {
        /**
         * Repeatable annotation value.
         *
         * @return repeatable annotation
         */
        Roles[] value();
    }

    /**
     * Attribute configuration class for Role validator.
     */
    public static final class RoleConfig implements AbacValidatorConfig {
        private final Set<String> userRolesAllowed = new HashSet<>();
        private final Set<String> serviceRolesAllowed = new HashSet<>();
        private boolean permitAll;
        private boolean denyAll;

        private RoleConfig(Builder builder) {
            this.permitAll = builder.permitAll;
            this.denyAll = builder.denyAll;
            this.userRolesAllowed.addAll(builder.userRolesAllowed);
            this.serviceRolesAllowed.addAll(builder.serviceRolesAllowed);
        }

        /**
         * A new builder for this class instances.
         *
         * @return builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Create roles config from a collection of allowed roles.
         *
         * @param rolesAllowed roles allowed
         * @return instance configured with the userRolesAllowed
         */
        public static RoleConfig create(Collection<String> rolesAllowed) {
            return RoleConfig.builder()
                    .addRoles(rolesAllowed)
                    .build();
        }

        /**
         * Create roles config from an array of allowed roles.
         *
         * @param rolesAllowed roles allowed
         * @return instance configured with the userRolesAllowed
         */
        public static RoleConfig create(String... rolesAllowed) {
            return RoleConfig.builder()
                    .addRoles(Arrays.asList(rolesAllowed))
                    .build();
        }

        /**
         * Will read roles allowed from configuration.
         * Format (yaml):
         * <pre>
         * roles-allowed:
         *  user: ["role1","role2"]
         *  service: ["role3]
         * </pre>
         *
         * @param config configuration located on key "roles-allowed"
         * @return roles config for the configuration
         */
        public static RoleConfig create(Config config) {
            return builder().config(config).build();
        }

        /**
         * Set of roles required for a service.
         *
         * @return set of roles
         */
        public Set<String> serviceRolesAllowed() {
            return Collections.unmodifiableSet(serviceRolesAllowed);
        }

        /**
         * Set of roles required for a user.
         *
         * @return set of roles
         */
        public Set<String> userRolesAllowed() {
            return Collections.unmodifiableSet(userRolesAllowed);
        }

        /**
         * Returns true if access should be permitted to all.
         *
         * @return permitted access to all
         */
        public boolean permitAll() {
            return permitAll;
        }

        /**
         * Returns true if access should be denied to all.
         *
         * @return denied access to all
         */
        public boolean denyAll() {
            return denyAll;
        }

        /**
         * A fluent API builder for {@link RoleConfig}.
         */
        public static class Builder implements io.helidon.common.Builder<RoleConfig> {
            private final Set<String> userRolesAllowed = new LinkedHashSet<>();
            private final Set<String> serviceRolesAllowed = new LinkedHashSet<>();
            private boolean permitAll = false;
            private boolean denyAll = false;

            @Override
            public RoleConfig build() {
                return new RoleConfig(this);
            }

            /**
             * Add a collection of roles for user subject to this builder.
             *
             * @param rolesAllowed collection of roles, iterator order will be preserved for checking the roles
             * @return updated builder instance
             */
            public Builder addRoles(Collection<String> rolesAllowed) {
                this.userRolesAllowed.addAll(rolesAllowed);
                return this;
            }

            /**
             * Clears all roles currently set to this builder.
             *
             * @return updated builder instance
             */
            public Builder clearRoles() {
                this.userRolesAllowed.clear();
                return this;
            }

            /**
             * Clears all service roles currently set to this builder.
             *
             * @return updated builder instance
             */
            public Builder clearServiceRoles() {
                this.serviceRolesAllowed.clear();
                return this;
            }

            /**
             * Add a role to the list of roles for a user subject. Role will be added only once (e.g. this builder is using
             * a linked hash set to store the roles).
             *
             * @param role a role, order of calls to this method will be preserved
             * @return updated builder instance
             */
            public Builder addRole(String role) {
                this.userRolesAllowed.add(role);
                return this;
            }

            /**
             * Add a collection of roles for service subject to this builder.
             *
             * @param rolesAllowed collection of roles, iterator order will be preserved for checking the roles
             * @return updated builder instance
             */
            public Builder addServiceRoles(Collection<String> rolesAllowed) {
                this.serviceRolesAllowed.addAll(rolesAllowed);
                return this;
            }

            /**
             * Add a role to the list of roles for a service subject.
             *
             * @param role a role to add
             * @return updated builder instance
             */
            private Builder addServiceRole(String role) {
                this.serviceRolesAllowed.add(role);
                return this;
            }

            /**
             * Sets if all access should be permitted.
             *
             * @param permitAll if access should be permitted for all
             * @return updated builder instance
             */
            private Builder permitAll(boolean permitAll) {
                this.permitAll = permitAll;
                return this;
            }

            /**
             * Sets if all access should be denied.
             *
             * @param denyAll if access should be denied for all
             * @return updated builder instance
             */
            private Builder denyAll(boolean denyAll) {
                this.denyAll = denyAll;
                return this;
            }

            /**
             * Load configuration data from configuration.
             *
             * @param config configuration located the key of this attribute config
             * @return updated builder instance
             */
            public Builder config(Config config) {
                config.get("user").asList(String.class).ifPresent(this::addRoles);
                config.get("service").asList(String.class).ifPresent(this::addServiceRoles);
                config.get("permit-all").asBoolean().ifPresent(this::permitAll);
                config.get("deny-all").asBoolean().ifPresent(this::denyAll);
                return this;
            }
        }
    }
}
