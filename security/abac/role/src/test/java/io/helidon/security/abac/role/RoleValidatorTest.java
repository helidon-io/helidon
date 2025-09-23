/*
 * Copyright (c) 2018, 2025 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.Errors;
import io.helidon.common.types.Annotation;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityLevel;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;
import io.helidon.security.providers.abac.AbacProvider;

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RoleValidator}.
 */
class RoleValidatorTest {

    static Config config;

    @BeforeAll
    static void beforeAll() {
        config = Config.create();
    }


    @Test
    void testRolesAllowedPermit() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE)
                                            .putValue("value", List.of("admin"))
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testRolesAllowedDeny() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE)
                                            .putValue("value", List.of("admin"))
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("User is not in admin role, should have failed");
        }
    }

    @Test
    void testUserRoles() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(RoleValidator.Roles.TYPE)
                                            .putValue("value", List.of("admin"))
                                            .putValue("subjectType", SubjectType.USER)
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testUserRolesDeny() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(RoleValidator.Roles.TYPE)
                                            .putValue("value", List.of("admin"))
                                            .putValue("subjectType", SubjectType.USER)
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("User is not in admin role, should have failed");
        }
    }

    @Test
    void testServiceRoles() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(RoleValidator.Roles.TYPE)
                                            .putValue("value", List.of("admin"))
                                            .putValue("subjectType", SubjectType.SERVICE)
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.service()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("service"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testServiceRolesDeny() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(RoleValidator.Roles.TYPE)
                                            .putValue("value", List.of("admin"))
                                            .putValue("subjectType", SubjectType.SERVICE)
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.service()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("service"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("Service is not in admin role, should have failed");
        }
    }

    @Test
    void testDenyAll() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.DENY_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.create(AbacProvider.DENY_ALL_JAKARTA_TYPE)));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("user"))
                                                               .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("DenyAll is set on this method, this should have failed");
        }
    }

    @Test
    void testPermitAll() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.PERMIT_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.create(AbacProvider.PERMIT_ALL_JAKARTA_TYPE)));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("user"))
                                                               .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testDenyAllAndPermitAll() {
        RoleValidator validator = RoleValidator.create();
        PermitAll permitAll = mock(PermitAll.class);
        doReturn(PermitAll.class).when(permitAll).annotationType();
        DenyAll denyAll = mock(DenyAll.class);
        doReturn(DenyAll.class).when(denyAll).annotationType();

        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(SecurityLevel.builder()
                                   .type(TypeName.create(RoleValidatorTest.class))
                                   .methodName("firstLevel")
                                   .build());
        securityLevels.add(SecurityLevel.builder()
                                   .type(TypeName.create(RoleValidatorTest.class))
                                   .methodName("secondLevel")
                                   .addClassAnnotation(denyAll)
                                   .addMethodAnnotation(permitAll)
                                   .build());

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("user"))
                                                               .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testDenyAllAndRoles() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.DENY_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(Annotation.create(AbacProvider.DENY_ALL_JAKARTA_TYPE)));
        when(classSecurityLevel.filterAnnotations(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE)
                                            .putValue("value", List.of("admin"))
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("admin"))
                                                               .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testPermitAllAndRolesAndDenyAll() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.PERMIT_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(Annotation.create(AbacProvider.PERMIT_ALL_JAKARTA_TYPE)));
        when(classSecurityLevel.filterAnnotations(AbacProvider.DENY_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.create(AbacProvider.DENY_ALL_JAKARTA_TYPE)));
        when(classSecurityLevel.filterAnnotations(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE)
                                            .putValue("value", List.of("admin"))
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("admin"))
                                                               .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("DenyAll is set on this method, this should have failed");
        }
    }

    @Test
    void testAllAccessAnnotationsOnTheSameLevel() {
        RoleValidator validator = RoleValidator.create();

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(AbacProvider.PERMIT_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.create(AbacProvider.PERMIT_ALL_JAKARTA_TYPE)));
        when(classSecurityLevel.filterAnnotations(AbacProvider.DENY_ALL_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.create(AbacProvider.DENY_ALL_JAKARTA_TYPE)));
        when(classSecurityLevel.filterAnnotations(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(Annotation.builder()
                                            .typeName(AbacProvider.ROLES_ALLOWED_JAKARTA_TYPE)
                                            .putValue("value", List.of("admin"))
                                            .build()));

        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(ep);

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("admin"))
                                                               .build()));
        when(request.service()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("DenyAll is set on this method, this should have failed");
        }
    }

    @Test
    void testRolesFromConfig() {
        RoleValidator roleValidator = RoleValidator.create();
        RoleValidator.RoleConfig roleConfig = roleValidator.fromConfig(config.get("test1"));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("admin"))
                                                               .build()));

        roleValidator.validate(roleConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testRolesAndPermitAllFromConfig() {
        RoleValidator roleValidator = RoleValidator.create();
        RoleValidator.RoleConfig roleConfig = roleValidator.fromConfig(config.get("test2"));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("test"))
                                                               .build()));

        roleValidator.validate(roleConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testRolesAndDenyAllFromConfig() {
        RoleValidator roleValidator = RoleValidator.create();
        RoleValidator.RoleConfig roleConfig = roleValidator.fromConfig(config.get("test3"));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("test"))
                                                               .build()));

        roleValidator.validate(roleConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("DenyAll is set on this method, this should have failed");
        }
    }

    @Test
    void testAllAccessModificationsOnTheSameLevelFromConfig() {
        RoleValidator roleValidator = RoleValidator.create();
        RoleValidator.RoleConfig roleConfig = roleValidator.fromConfig(config.get("test4"));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.subject()).thenReturn(Optional.of(Subject.builder()
                                                               .principal(Principal.create("myAdmin"))
                                                               .addGrant(Role.create("test"))
                                                               .build()));

        roleValidator.validate(roleConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("DenyAll is set on this method, this should have failed");
        }
    }
}
