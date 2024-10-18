/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.security.abac.policy;

import io.helidon.security.abac.policy.PolicyValidator;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * A resource with abac policy statements.
 */
@Path("/policy")
public class PolicyStatementResource {

    /**
     * Policy statement configured via annotation.
     *
     * @return passed value
     */
    @GET
    @Path("/annotation")
    @PolicyValidator.PolicyStatement("${env.time.year >= 2017}")
    public String annotation() {
        return "passed";
    }

    /**
     * Policy statement overridden by the config.
     *
     * @return passed value
     */
    @GET
    @Path("/override")
    @PolicyValidator.PolicyStatement("${env.time.year <= 2017}")
    public String override() {
        return "passed";
    }

    /**
     * Policy statement should not be overridden by the config.
     *
     * @return passed value
     */
    @GET
    @Path("/override2")
    @PolicyValidator.PolicyStatement("${env.time.year <= 2017}")
    public String override2() {
        return "should not pass";
    }

    /**
     * Policy statement overridden by the config with asterisk present in path.
     *
     * @return passed value
     */
    @GET
    @Path("/asterisk")
    @PolicyValidator.PolicyStatement("${env.time.year <= 2017}")
    public String asterisk() {
        return "passed";
    }

    /**
     * Policy statement overridden by the config with asterisk present in path.
     *
     * @return passed value
     */
    @GET
    @Path("/asterisk2")
    @PolicyValidator.PolicyStatement("${env.time.year <= 2017}")
    public String asterisk2() {
        return "passed";
    }

    /**
     * Policy statement not overridden by configuration and should not let anyone in.
     *
     * @return should not pass value
     */
    @GET
    @Path("/notOverride")
    @PolicyValidator.PolicyStatement("${env.time.year <= 2017}")
    public String notOverride() {
        return "should not pass";
    }

}
