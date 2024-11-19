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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

/**
 * Policy statement matched by the method test resource.
 */
@Path("method")
public class PolicyStatementMethod {

    /**
     * Endpoint which should be matched via method.
     *
     * @return passed value
     */
    @GET
    @PolicyValidator.PolicyStatement("${env.time.year < 2017}")
    public String get() {
        return "passed";
    }

    /**
     * Endpoint which should not be matched because of the different method.
     *
     * @return should not pass value
     */
    @POST
    @PolicyValidator.PolicyStatement("${env.time.year < 2017}")
    public String post() {
        return "should not pass";
    }

}
