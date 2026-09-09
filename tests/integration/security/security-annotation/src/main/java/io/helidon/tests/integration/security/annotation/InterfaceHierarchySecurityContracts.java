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

import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

/**
 * Resource contracts used to verify interface annotation ownership.
 */
public final class InterfaceHierarchySecurityContracts {

    private InterfaceHierarchySecurityContracts() {
    }

    /**
     * An unrelated contract implemented by the resource handler.
     */
    public interface Unrelated {
    }

    /**
     * Root of the selected resource contract hierarchy.
     */
    public interface Root {
    }

    /**
     * Left side of the selected resource contract hierarchy.
     */
    @RolesAllowed("admin")
    public interface Left extends Root {
    }

    /**
     * Right side of the selected resource contract hierarchy.
     */
    public interface Right extends Root {
    }

    /**
     * Generic parent contract used to verify specialized handler signatures.
     *
     * @param <T> endpoint parameter type
     */
    public interface GenericSecurityContract<T> {

        /**
         * Endpoint secured on its generic contract method.
         *
         * @param value endpoint value
         * @return endpoint response
         */
        @GET
        @Path("generic/{value}")
        @DenyAll
        String genericSecured(@PathParam("value") T value);
    }

    /**
     * Selected JAX-RS resource contract.
     */
    @Path("/interface-hierarchy-security")
    public interface Resource extends Right, Left {

        /**
         * Endpoint whose ancestor interface security must not be applied.
         *
         * @return endpoint response
         */
        @GET
        String get();

        /**
         * Endpoint whose unrelated private superclass method must not contribute security.
         *
         * @return endpoint response
         */
        @GET
        @Path("private")
        String privateSuperclass();
    }

    /**
     * Parent contract declaring an endpoint inherited by a selected child contract.
     */
    public interface ParentEndpoint {

        /**
         * Returns the endpoint response.
         *
         * @return endpoint response
         */
        @GET
        String get();
    }

    /**
     * Selected contract whose class security must not affect its parent-declared endpoint.
     */
    @Path("/interface-hierarchy-security-reversed")
    @RolesAllowed("admin")
    public interface ReversedResource extends Left, Right, ParentEndpoint {
    }

    /**
     * Sibling contract declaring an endpoint on the selected resource handler.
     */
    public interface SiblingEndpoint {

        /**
         * Endpoint secured by the sibling {@link Resource} contract.
         *
         * @return endpoint response
         */
        @GET
        @Path("sibling")
        String sibling();
    }

    /**
     * Base class whose declarations have the same signatures as the actual resource handlers.
     */
    public static class OverriddenBase {

        /**
         * Public method overridden by the actual resource handler.
         *
         * @return base response
         */
        @RolesAllowed("admin")
        public String get() {
            return "base";
        }

        /**
         * Private method that must not contribute security to the resource handler.
         *
         * @return base response
         */
        @RolesAllowed("admin")
        private String privateSuperclass() {
            return "base";
        }
    }
}
