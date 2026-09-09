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

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;

/**
 * Handler for the interface-defined subresource contracts.
 */
@RequestScoped
public class InterfaceSubresourceSecurityResourceImpl
        extends InterfaceSubresourceSecurityContracts.GenericHandlingLocator<
                InterfaceSubresourceSecurityContracts.IntermediateSubresource,
                InterfaceSubresourceSecurityContracts.SecuredSubresource>
        implements InterfaceSubresourceSecurityContracts.Resource {

    @Override
    public InterfaceSubresourceSecurityContracts.Subresource locate() {
        return new SubresourceImpl();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.Subresource locateOpen() {
        return new SubresourceImpl();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.InheritedSubresource locateInherited() {
        return new InterfaceSubresourceSecurityContracts.InheritedSubresource();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.InheritedClassSubresource locateInheritedClass() {
        return new InterfaceSubresourceSecurityContracts.InheritedClassSubresource();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.InheritedClassSubresource locateBroadInheritedClass() {
        return new InterfaceSubresourceSecurityContracts.InheritedClassSubresource();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.HandlingSecuredInheritedSubresource locateBroadInheritedMethod() {
        return new InterfaceSubresourceSecurityContracts.HandlingSecuredInheritedSubresource();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.HandlingPermitAllInheritedSubresource locateBroadInheritedRoleMethod() {
        return new InterfaceSubresourceSecurityContracts.HandlingPermitAllInheritedSubresource();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.NestedSubresource locateNested() {
        return new NestedSubresourceImpl();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.PathChild locatePathGrandparent() {
        return new PathGrandparentSubresourceImpl();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.SecuredSubresource locateCovariant() {
        return new GenericSubresourceImpl();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.BroadSubresource locateBroad() {
        return new BroadSubresourceImpl();
    }

    @Override
    public InterfaceSubresourceSecurityContracts.ImplementationSubresource locateImplementation() {
        return new ImplementationSubresourceImpl();
    }

    @Override
    protected InterfaceSubresourceSecurityContracts.SecuredSubresource createHandlingGeneric() {
        return new GenericSubresourceImpl();
    }

    @Override
    public GenericSubresourceImpl locateGeneric() {
        return new GenericSubresourceImpl();
    }

    /**
     * Final subresource handler.
     */
    public static final class SubresourceImpl
            implements InterfaceSubresourceSecurityContracts.UnrelatedSubresource,
            InterfaceSubresourceSecurityContracts.Subresource {

        @Override
        public String get() {
            return "interface subresource";
        }
    }

    /**
     * Handler for the specialized generic subresource contract.
     */
    public static final class GenericSubresourceImpl
            implements InterfaceSubresourceSecurityContracts.UnrelatedSubresource,
            InterfaceSubresourceSecurityContracts.SecuredSubresource {

        @Override
        public String get() {
            return "generic interface subresource";
        }
    }

    /**
     * Handler selected through a broad locator return type.
     */
    public static final class BroadSubresourceImpl
            implements InterfaceSubresourceSecurityContracts.SecuredBroadSubresource {

        @Override
        public String get() {
            return "broad interface subresource";
        }
    }

    /**
     * Handler declaring class- and method-level security.
     */
    @RolesAllowed("admin")
    public static final class ImplementationSubresourceImpl
            implements InterfaceSubresourceSecurityContracts.ImplementationSubresource {

        @Override
        public String get() {
            return "implementation subresource";
        }

        @Override
        @PermitAll
        public String open() {
            return "open implementation subresource";
        }
    }

    /**
     * Handler whose resource contract inherits a class-level path from its grandparent.
     */
    public static final class PathGrandparentSubresourceImpl
            implements InterfaceSubresourceSecurityContracts.PathChild {

        @Override
        public String get() {
            return "path grandparent subresource";
        }
    }

    /**
     * Intermediate handler whose selected contract carries the security declaration.
     */
    public static final class NestedSubresourceImpl
            implements InterfaceSubresourceSecurityContracts.NestedSubresource {

        @Override
        public InterfaceSubresourceSecurityContracts.Subresource locateFinal() {
            return new SubresourceImpl();
        }
    }
}
