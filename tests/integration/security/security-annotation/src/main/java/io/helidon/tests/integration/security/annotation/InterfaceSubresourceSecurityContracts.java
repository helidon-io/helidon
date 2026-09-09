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

import io.helidon.security.AuditEvent.AuditSeverity;
import io.helidon.security.annotations.Audited;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Contracts used to verify security on interface-defined subresources.
 */
public final class InterfaceSubresourceSecurityContracts {

    private InterfaceSubresourceSecurityContracts() {
    }

    /**
     * Selected root resource contract.
     */
    @Path("/interface-subresource-security")
    @RolesAllowed("admin")
    public interface Resource extends Locator,
                                      GenericLocator<SecuredSubresource>,
                                      LayeredGenericLocator<IntermediateSubresource> {
    }

    /**
     * Parent contract declaring the inherited subresource locator.
     */
    public interface Locator {

        /**
         * Locates the final subresource.
         *
         * @return subresource instance
         */
        @Path("sub")
        Subresource locate();

        /**
         * Locates the final subresource without requiring authentication.
         *
         * @return subresource instance
         */
        @Path("open")
        Subresource locateOpen();

        /**
         * Locates a class-based subresource with an inherited endpoint method.
         *
         * @return subresource instance
         */
        @Path("inherited")
        InheritedSubresource locateInherited();

        /**
         * Locates a class-based subresource whose superclass declares security.
         *
         * @return inherited class subresource instance
         */
        @Path("inherited-class")
        InheritedClassSubresource locateInheritedClass();

        /**
         * Locates a secured class-based subresource through a broad return type.
         *
         * @return broad class-based subresource instance
         */
        @Path("inherited-class-broad")
        BroadClassSubresource locateBroadInheritedClass();

        /**
         * Locates a handler whose class security must not affect its inherited method.
         *
         * @return broad class-based subresource instance
         */
        @Path("inherited-method-broad")
        BroadClassSubresource locateBroadInheritedMethod();

        /**
         * Locates a handler whose class permit-all policy must not affect its inherited role-secured method.
         *
         * @return broad class-based subresource instance
         */
        @Path("inherited-role-method-broad")
        BroadClassSubresource locateBroadInheritedRoleMethod();

        /**
         * Locates an interface-defined nested subresource.
         *
         * @return nested subresource instance
         */
        @Path("nested")
        NestedSubresource locateNested();

        /**
         * Locates a subresource whose inherited resource path must not change its routing classification.
         *
         * @return path-inheriting subresource instance
         */
        @Path("path-grandparent")
        PathChild locatePathGrandparent();

        /**
         * Locates a secured subresource using a covariant implementation return type.
         *
         * @return base subresource contract
         */
        @Path("covariant")
        Endpoint locateCovariant();

        /**
         * Locates a secured subresource through its broad contract.
         *
         * @return broad subresource contract
         */
        @Path("broad")
        BroadSubresource locateBroad();

        /**
         * Locates a subresource whose implementation declares security.
         *
         * @return implementation-secured subresource
         */
        @Path("implementation")
        ImplementationSubresource locateImplementation();

        /**
         * Locates a secured subresource through a generic handling method.
         *
         * @return base subresource contract
         */
        @Path("handling-generic")
        Endpoint locateHandlingGeneric();
    }

    /**
     * Generic parent contract declaring a specialized subresource locator.
     *
     * @param <T> subresource contract type
     */
    public interface GenericLocator<T extends Endpoint> {

        /**
         * Locates a specialized secured subresource.
         *
         * @return secured subresource instance
         */
        @Path("generic")
        T locateGeneric();
    }

    /**
     * Generic locator contract resolved to an intermediate subresource type.
     *
     * @param <T> intermediate subresource contract type
     */
    public interface LayeredGenericLocator<T extends Endpoint> {

        /**
         * Locates a subresource specialized further by the handling method.
         *
         * @return specialized subresource instance
         */
        @Path("layered-generic")
        T locateLayeredGeneric();
    }

    /**
     * Final subresource contract without a type-level {@link Path}.
     */
    @Audited(value = "interface-subresource",
             messageFormat = "Interface subresource request",
             okSeverity = AuditSeverity.INFO,
             errorSeverity = AuditSeverity.ERROR)
    public interface Subresource extends Endpoint {
    }

    /**
     * Unrelated contract implemented by the final subresource handler.
     */
    public interface UnrelatedSubresource extends Endpoint {
    }

    /**
     * Selected specialization whose class security does not affect its parent-declared endpoint.
     */
    @RolesAllowed("admin")
    public interface SecuredSubresource extends IntermediateSubresource {
    }

    /**
     * Intermediate generic locator specialization without endpoint security.
     */
    public interface IntermediateSubresource extends Endpoint {
    }

    /**
     * Broad locator return type without endpoint annotations.
     */
    public interface BroadSubresource {
    }

    /**
     * Secured endpoint contract selected from a broad locator return type.
     */
    @RolesAllowed("admin")
    public interface SecuredBroadSubresource extends BroadSubresource {

        /**
         * Returns the secured response.
         *
         * @return endpoint response
         */
        @GET
        String get();
    }

    /**
     * Subresource contract whose implementation supplies security.
     */
    public interface ImplementationSubresource {

        /**
         * Returns the implementation-secured response.
         *
         * @return endpoint response
         */
        @GET
        String get();

        /**
         * Returns the implementation method override response.
         *
         * @return endpoint response
         */
        @GET
        @Path("open")
        String open();
    }

    /**
     * Grandparent resource contract whose path is not inherited by its child contract.
     */
    @Path("/ignored")
    public interface PathGrandparent {

        /**
         * Returns the secured response.
         *
         * @return endpoint response
         */
        @GET
        String get();
    }

    /**
     * Unbound subresource contract inheriting its endpoint from a path-annotated grandparent.
     */
    @RolesAllowed("admin")
    public interface PathChild extends PathGrandparent {

        @Override
        @GET
        String get();
    }

    /**
     * Selected intermediate subresource contract.
     */
    @RolesAllowed("admin")
    public interface NestedSubresource extends NestedLocator {

        @Override
        @Path("final")
        Subresource locateFinal();
    }

    /**
     * Parent contract declaring the nested locator.
     */
    public interface NestedLocator {

        /**
         * Locates the final subresource.
         *
         * @return final subresource instance
         */
        @Path("final")
        Subresource locateFinal();
    }

    /**
     * Parent contract declaring the final endpoint.
     */
    public interface Endpoint {

        /**
         * Returns the response. Interface security must not override concrete security on the endpoint path.
         *
         * @return endpoint response
         */
        @GET
        @PermitAll
        String get();
    }

    /**
     * Generic implementation of a broadly declared locator method.
     *
     * @param <U> intermediate subresource contract
     * @param <T> specialized subresource contract
     */
    public abstract static class GenericHandlingLocator<U extends Endpoint, T extends U>
            extends ConcreteLocatorBase
            implements Locator, LayeredGenericLocator<U> {

        @Override
        public T locateHandlingGeneric() {
            return createHandlingGeneric();
        }

        @Override
        public T locateLayeredGeneric() {
            return createHandlingGeneric();
        }

        /**
         * Creates the specialized subresource instance.
         *
         * @return specialized subresource instance
         */
        protected abstract T createHandlingGeneric();
    }

    /**
     * Secured concrete superclass declaring a locator inherited by the root resource handler.
     */
    @RolesAllowed("admin")
    public static class ConcreteLocatorBase {

        /**
         * Locates an otherwise unsecured endpoint.
         *
         * @return endpoint instance
         */
        @Path("superclass-locator")
        public Endpoint locateFromConcreteSuperclass() {
            return new OpenSubresource();
        }
    }

    /**
     * Concrete subresource whose inherited method security must remain most specific.
     */
    @PermitAll
    public static final class InheritedSubresource extends InheritedSubresourceBase {
    }

    /**
     * Concrete subresource inheriting class-level security from its endpoint definition.
     */
    public static final class InheritedClassSubresource extends SecuredInheritedSubresource {
    }

    /**
     * Broad class-based subresource return type without endpoint security.
     */
    public static class BroadClassSubresource {
    }

    /**
     * Endpoint definition whose method permits anonymous access.
     */
    public static class PermitAllInheritedSubresource extends BroadClassSubresource {

        /**
         * Returns the secured response.
         *
         * @return endpoint response
         */
        @GET
        @PermitAll
        public String get() {
            return "inherited method subresource";
        }
    }

    /**
     * Concrete handler whose class security does not apply to its inherited endpoint method.
     */
    @RolesAllowed("admin")
    public static final class HandlingSecuredInheritedSubresource extends PermitAllInheritedSubresource {
    }

    /**
     * Concrete handler whose permit-all policy does not apply to its inherited endpoint method.
     */
    @PermitAll
    public static final class HandlingPermitAllInheritedSubresource extends InheritedSubresourceBase {
    }

    /**
     * Base class declaring the inherited endpoint method.
     */
    public static class InheritedSubresourceBase extends BroadClassSubresource {

        /**
         * Returns the secured response.
         *
         * @return endpoint response
         */
        @GET
        @RolesAllowed("admin")
        public String get() {
            return "inherited subresource";
        }
    }

    /**
     * Secured class declaring an endpoint inherited by the concrete subresource.
     */
    @RolesAllowed("admin")
    public static class SecuredInheritedSubresource extends BroadClassSubresource {

        /**
         * Returns the secured response.
         *
         * @return endpoint response
         */
        @GET
        public String get() {
            return "inherited class subresource";
        }
    }

    /**
     * Endpoint reached through a locator inherited from a concrete superclass.
     */
    public static final class OpenSubresource implements Endpoint {

        @Override
        public String get() {
            return "concrete superclass locator";
        }
    }

}
