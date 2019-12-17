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

package io.helidon.security.abac.role;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;

import io.helidon.common.Errors;
import io.helidon.config.Config;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.SecurityLevel;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
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
        RolesAllowed annot = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RolesAllowed.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        RolesAllowed annot = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RolesAllowed.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        RoleValidator.Roles annot = mock(RoleValidator.Roles.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);
        when(annot.subjectType()).thenReturn(SubjectType.USER);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        RoleValidator.Roles annot = mock(RoleValidator.Roles.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.subjectType()).thenReturn(SubjectType.USER);
        when(annot.value()).thenReturn(roleArray);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        RoleValidator.Roles annot = mock(RoleValidator.Roles.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);
        when(annot.subjectType()).thenReturn(SubjectType.SERVICE);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        RoleValidator.Roles annot = mock(RoleValidator.Roles.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);
        when(annot.subjectType()).thenReturn(SubjectType.SERVICE);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(RoleValidator.Roles.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        DenyAll annot = mock(DenyAll.class);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(DenyAll.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        PermitAll annot = mock(PermitAll.class);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(PermitAll.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(annot));

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
        DenyAll denyAll = mock(DenyAll.class);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(DenyAll.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(denyAll));
        when(classSecurityLevel.filterAnnotations(PermitAll.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(permitAll));

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
        DenyAll denyAll = mock(DenyAll.class);
        RolesAllowed rolesAllowed = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(rolesAllowed.value()).thenReturn(roleArray);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(DenyAll.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(denyAll));
        when(classSecurityLevel.filterAnnotations(RolesAllowed.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(rolesAllowed));

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
        PermitAll permitAll = mock(PermitAll.class);
        DenyAll denyAll = mock(DenyAll.class);
        RolesAllowed rolesAllowed = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(rolesAllowed.value()).thenReturn(roleArray);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(PermitAll.class, EndpointConfig.AnnotationScope.CLASS))
                .thenReturn(List.of(permitAll));
        when(classSecurityLevel.filterAnnotations(DenyAll.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(denyAll));
        when(classSecurityLevel.filterAnnotations(RolesAllowed.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(rolesAllowed));

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
        PermitAll permitAll = mock(PermitAll.class);
        DenyAll denyAll = mock(DenyAll.class);
        RolesAllowed rolesAllowed = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(rolesAllowed.value()).thenReturn(roleArray);

        SecurityLevel appSecurityLevel = mock(SecurityLevel.class);
        SecurityLevel classSecurityLevel = mock(SecurityLevel.class);
        List<SecurityLevel> securityLevels = new ArrayList<>();
        securityLevels.add(appSecurityLevel);
        securityLevels.add(classSecurityLevel);

        EndpointConfig ep = mock(EndpointConfig.class);
        when(ep.securityLevels()).thenReturn(securityLevels);
        when(classSecurityLevel.filterAnnotations(PermitAll.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(permitAll));
        when(classSecurityLevel.filterAnnotations(DenyAll.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(denyAll));
        when(classSecurityLevel.filterAnnotations(RolesAllowed.class, EndpointConfig.AnnotationScope.METHOD))
                .thenReturn(List.of(rolesAllowed));

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
