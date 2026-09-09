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

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.security.abac.role.RoleValidator;
import io.helidon.security.annotations.Authenticated;
import io.helidon.security.annotations.Authorized;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

/**
 * Resource used to verify inherited security after broad locator normalization.
 */
@Path("/inherited-handler-security")
@RequestScoped
public class InheritedHandlerSecurityResource {

    /**
     * Locates the authenticated handler through a broad return type.
     *
     * @return broad subresource instance
     */
    @Path("sub")
    public BroadSubresource locate() {
        return new InheritedHandler();
    }

    /**
     * Locates the authenticated handler through a broad interface return type.
     *
     * @return broad subresource instance
     */
    @Path("interface")
    public BroadSubresourceContract locateInterface() {
        return new InheritedHandler();
    }

    /**
     * Locates a handler whose inherited authentication is overridden by its endpoint method.
     *
     * @return broad subresource instance
     */
    @Path("method")
    public BroadSubresource locateMethodSecurity() {
        return new MethodSecurityHandler();
    }

    /**
     * Locates a handler whose declared class authentication must not affect its inherited endpoint method.
     *
     * @return broad subresource instance
     */
    @Path("declared-authentication-disabled")
    public BroadSubresource locateDeclaredAuthenticationDisabled() {
        return new DeclaredAuthenticationDisabledHandler();
    }

    /**
     * Locates a handler whose declared class authorization must not affect its inherited endpoint method.
     *
     * @return broad subresource instance
     */
    @Path("declared-authorization-disabled")
    public BroadSubresource locateDeclaredAuthorizationDisabled() {
        return new DeclaredAuthorizationDisabledHandler();
    }

    /**
     * Locates a handler with an inherited legacy analyzer annotation.
     *
     * @return broad subresource instance
     */
    @Path("legacy-analyzer-inherited")
    public BroadSubresource locateInheritedLegacyAnalyzerSecurity() {
        return new InheritedLegacyAnalyzerHandler();
    }

    /**
     * Locates a handler with a directly declared legacy analyzer annotation.
     *
     * @return broad subresource instance
     */
    @Path("legacy-analyzer-declared")
    public BroadSubresource locateDeclaredLegacyAnalyzerSecurity() {
        return new DeclaredLegacyAnalyzerHandler();
    }

    /**
     * Locates a handler with an inherited metadata-based analyzer annotation.
     *
     * @return broad subresource instance
     */
    @Path("role-analyzer-inherited")
    public BroadSubresource locateInheritedRoleAnalyzerSecurity() {
        return new InheritedRoleAnalyzerHandler();
    }

    /**
     * Locates an implementation-owned endpoint secured by a classic analyzer class callback.
     *
     * @return broad subresource instance
     */
    @Path("classic-analyzer")
    public BroadSubresource locateClassicAnalyzerSecurity() {
        return new ClassicAnalyzerHandler();
    }

    /**
     * Locates overloaded methods analyzed through the classic subresource callback sequence.
     *
     * @return broad subresource instance
     */
    @Path("classic-overloads")
    public BroadSubresource locateClassicOverloads() {
        return new ClassicOverloadHandler();
    }

    /**
     * Broad locator interface without endpoint security.
     */
    public interface BroadSubresourceContract {
    }

    /**
     * Marker interpreted by a legacy reflection-based annotation analyzer.
     */
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LegacyAuthenticationDisabled {
    }

    /**
     * Marker interpreted by a legacy reflection-based annotation analyzer.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface LegacyAuthenticationRequired {
    }

    /**
     * Marker interpreted by the classic metadata-based annotation analyzer.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ClassicAuthenticationRequired {
    }

    /**
     * Broad locator return type without endpoint security.
     */
    public static class BroadSubresource implements BroadSubresourceContract {
    }

    /**
     * Superclass declaring the endpoint method without security.
     */
    public static class EndpointBase extends BroadSubresource {

        /**
         * Returns the authenticated response.
         *
         * @return endpoint response
         */
        @GET
        public String get() {
            return "inherited handler security";
        }
    }

    /**
     * Intermediate superclass providing inherited authentication.
     */
    @Authenticated
    public static class AuthenticatedHandlerBase extends EndpointBase {
    }

    /**
     * Concrete endpoint handler inheriting its endpoint method and authentication.
     */
    public static final class InheritedHandler extends AuthenticatedHandlerBase {
    }

    /**
     * Handler base disabling authentication while requiring it on the endpoint method.
     */
    @Authenticated(false)
    public static class MethodSecurityBase extends BroadSubresource {

        /**
         * Returns the authenticated response.
         *
         * @return endpoint response
         */
        @GET
        @Authenticated
        public String get() {
            return "method security";
        }
    }

    /**
     * Concrete handler inheriting class and method security.
     */
    public static final class MethodSecurityHandler extends MethodSecurityBase {
    }

    /**
     * Base endpoint requiring authentication on an inherited method.
     */
    public static class AuthenticationRequiredEndpointBase extends BroadSubresource {

        /**
         * Returns the authenticated response.
         *
         * @return endpoint response
         */
        @GET
        @Authenticated
        public String get() {
            return "declared authentication disabled handler";
        }
    }

    /**
     * Concrete handler declaring authentication disabled while inheriting its secured endpoint method.
     */
    @Authenticated(false)
    public static final class DeclaredAuthenticationDisabledHandler extends AuthenticationRequiredEndpointBase {
    }

    /**
     * Base endpoint requiring the admin role on an inherited method.
     */
    public static class AuthorizationRequiredEndpointBase extends BroadSubresource {

        /**
         * Returns the authorized response.
         *
         * @return endpoint response
         */
        @GET
        @RolesAllowed("admin")
        public String get() {
            return "declared authorization disabled handler";
        }
    }

    /**
     * Concrete handler declaring authorization disabled while inheriting its secured endpoint method.
     */
    @Authorized(false)
    public static final class DeclaredAuthorizationDisabledHandler extends AuthorizationRequiredEndpointBase {
    }

    /**
     * Base endpoint requiring authentication independently of authorization.
     */
    public static class InheritedLegacyAnalyzerEndpointBase extends BroadSubresource {

        /**
         * Returns the analyzer precedence response.
         *
         * @return endpoint response
         */
        @GET
        @Authenticated
        @Authorized(false)
        public String get() {
            return "legacy analyzer security";
        }
    }

    /**
     * Intermediate handler base disabling authentication through a legacy analyzer.
     */
    @LegacyAuthenticationDisabled
    public static class LegacyAnalyzerHandlerBase extends InheritedLegacyAnalyzerEndpointBase {
    }

    /**
     * Handler inheriting the legacy analyzer annotation.
     */
    public static final class InheritedLegacyAnalyzerHandler extends LegacyAnalyzerHandlerBase {
    }

    /**
     * Base endpoint whose concrete handler declares the analyzer annotation.
     */
    @Authorized(false)
    public static class DeclaredLegacyAnalyzerEndpointBase extends BroadSubresource {

        /**
         * Returns the analyzer precedence response.
         *
         * @return endpoint response
         */
        @GET
        @Authenticated(false)
        @Authorized(false)
        public String get() {
            return "legacy analyzer security";
        }
    }

    /**
     * Handler declaring the legacy analyzer annotation directly.
     */
    @LegacyAuthenticationRequired
    public static final class DeclaredLegacyAnalyzerHandler extends DeclaredLegacyAnalyzerEndpointBase {
    }

    /**
     * Endpoint secured only by an analyzer-backed inherited role annotation.
     */
    public static class RoleAnalyzerEndpointBase extends BroadSubresource {

        /**
         * Returns the analyzer precedence response.
         *
         * @return endpoint response
         */
        @GET
        public String get() {
            return "inherited role analyzer security";
        }

        /**
         * Returns the method-level analyzer override response.
         *
         * @return endpoint response
         */
        @GET
        @Path("open")
        @RoleValidator.PermitAll
        public String open() {
            return "inherited role analyzer override";
        }
    }

    /**
     * Intermediate handler base requiring the admin role.
     */
    @RoleValidator.Roles("admin")
    public static class RoleAnalyzerHandlerBase extends RoleAnalyzerEndpointBase {
    }

    /**
     * Handler inheriting metadata-based analyzer security.
     */
    public static final class InheritedRoleAnalyzerHandler extends RoleAnalyzerHandlerBase {
    }

    /**
     * Concrete handler which owns an endpoint analyzed using the classic callback sequence.
     */
    public static final class ClassicAnalyzerHandler extends BroadSubresource {

        /**
         * Returns the classic analyzer response.
         *
         * @return endpoint response
         */
        @GET
        public String get() {
            return "classic analyzer security";
        }
    }

    /**
     * Handler with overloaded methods requiring isolated classic analyzer cache entries.
     */
    public static final class ClassicOverloadHandler extends BroadSubresource {

        /**
         * Returns the public overload response.
         *
         * @return endpoint response
         */
        @GET
        @Path("public")
        public String get() {
            return "classic overload public";
        }

        /**
         * Returns the authenticated overload response.
         *
         * @param value ignored query value used to create a distinct Java signature
         * @return endpoint response
         */
        @GET
        @Path("secured")
        @ClassicAuthenticationRequired
        public String get(@QueryParam("value") String value) {
            return "classic overload secured";
        }
    }
}
