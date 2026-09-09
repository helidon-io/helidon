/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.annotation;

import io.helidon.security.SecurityContext;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Handler implementing both the selected resource contract and an unrelated interface.
 */
@RequestScoped
public class InterfaceHierarchySecurityResourceImpl
        extends InterfaceHierarchySecurityContracts.OverriddenBase
        implements InterfaceHierarchySecurityContracts.Unrelated,
        InterfaceHierarchySecurityContracts.Resource,
        InterfaceHierarchySecurityContracts.SiblingEndpoint {

    @Inject
    private SecurityContext securityContext;

    @Override
    public String get() {
        String rootType = InterfaceHierarchySecurityContracts.Root.class.getName();
        String unrelatedType = InterfaceHierarchySecurityContracts.Unrelated.class.getName();
        long rootCount = securityContext.endpointConfig()
                .securityLevels()
                .stream()
                .filter(level -> rootType.equals(level.typeName().name()))
                .count();
        long unrelatedCount = securityContext.endpointConfig()
                .securityLevels()
                .stream()
                .filter(level -> unrelatedType.equals(level.typeName().name()))
                .count();
        return "root=" + rootCount + ", unrelated=" + unrelatedCount;
    }

    @Override
    public String sibling() {
        return "sibling endpoint";
    }

    @Override
    public String privateSuperclass() {
        return "private superclass";
    }
}
