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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/**
 * Resource contract used to verify security annotations on an implementation class.
 */
@Path("/interface-class-security")
public interface InterfaceClassSecurityResource {

    /**
     * Endpoint secured by its implementation class.
     *
     * @return endpoint response
     */
    String get();

    /**
     * Endpoint combining implementation-class security with definition-method audit metadata.
     *
     * @return endpoint response
     */
    @GET
    @Path("class-overrides-method")
    @Audited(value = "definition-method-resource",
             okSeverity = AuditSeverity.INFO)
    String classOverridesMethod();

    /**
     * Endpoint whose implementation method overrides class-level role security.
     *
     * @return endpoint response
     */
    String open();
}
