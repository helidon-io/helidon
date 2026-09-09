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
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Contracts used to verify security inherited by a class-defined root resource.
 */
public final class RootClassHierarchySecurityContracts {

    private RootClassHierarchySecurityContracts() {
    }

    /**
     * Secured class declaring the inherited endpoint method.
     */
    @DenyAll
    public abstract static class SecuredBase {

        /**
         * Returns the secured response.
         *
         * @return endpoint response
         */
        @GET
        public String get() {
            return "root class hierarchy";
        }

        /**
         * Returns the response from a method overridden without security.
         *
         * @return overridden response
         */
        @GET
        @Path("overridden")
        @DenyAll
        public String overridden() {
            return "base";
        }
    }

    /**
     * Selected root resource definition whose class security applies only to its own methods.
     */
    @Path("/root-class-hierarchy-security")
    @PermitAll
    public abstract static class Resource extends SecuredBase {

        /**
         * Returns an unsecured response declared directly on the resource definition.
         *
         * @return endpoint response
         */
        @GET
        @Path("open")
        public String open() {
            return "open root class hierarchy";
        }
    }
}
