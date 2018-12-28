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

package io.helidon.security.abac.role;

import java.util.Optional;

import javax.annotation.security.RolesAllowed;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.Errors;
import io.helidon.security.Principal;
import io.helidon.security.ProviderRequest;
import io.helidon.security.Role;
import io.helidon.security.Subject;
import io.helidon.security.SubjectType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RoleValidator}.
 */
class RoleValidatorTest {
    @Test
    void testRolesAllowedPermit() {
        RoleValidator validator = RoleValidator.create();
        RolesAllowed annot = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);
        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(CollectionsHelper.listOf(annot));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
        validator.validate(rConfig, collector, request);

        collector.collect().checkValid();
    }

    @Test
    void testRolesAllowedDeny() {
        RoleValidator validator = RoleValidator.create();
        RolesAllowed annot = mock(RolesAllowed.class);
        String[] roleArray = new String[] {"admin"};
        when(annot.value()).thenReturn(roleArray);
        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(CollectionsHelper.listOf(annot));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
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
        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(CollectionsHelper.listOf(annot));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
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
        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(CollectionsHelper.listOf(annot));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.empty());
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
        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(CollectionsHelper.listOf(annot));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getService()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("service"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
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
        RoleValidator.RoleConfig rConfig = validator.fromAnnotations(CollectionsHelper.listOf(annot));

        Errors.Collector collector = Errors.collector();
        ProviderRequest request = mock(ProviderRequest.class);
        when(request.getSubject()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("myAdmin"))
                                                                  .addGrant(Role.create("admin"))
                                                                  .build()));
        when(request.getService()).thenReturn(Optional.of(Subject.builder()
                                                                  .principal(Principal.create("service"))
                                                                  .addGrant(Role.create("user"))
                                                                  .build()));
        validator.validate(rConfig, collector, request);

        if (collector.collect().isValid()) {
            fail("Service is not in admin role, should have failed");
        }
    }
}
