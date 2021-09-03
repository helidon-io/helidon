/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

package io.helidon.security.abac.policy.spi;

import io.helidon.common.Errors;
import io.helidon.security.ProviderRequest;
import io.helidon.security.abac.policy.PolicyValidator;

/**
 * Custom executor of policy statements.
 */
public interface PolicyExecutor {
    /**
     * Can be used to tell the {@link PolicyValidator} that this statement is supported by this
     * policy executor. If there are more than one executors configured, first provider that supports a statement will execute it.
     *
     * @param policyStatement statement as configured through {@link io.helidon.security.EndpointConfig}
     * @param request         request of current security exchange, containing environment, subject, and object
     * @return true if this executor supports the statement, false otherwise
     */
    default boolean supports(String policyStatement, ProviderRequest request) {
        return true;
    }

    /**
     * Execute a policy based on a policy statement.
     *
     * @param policyStatement statement to evaluate
     * @param collector       register {@link Errors.Collector#fatal(Object, String)} in case the policy statement denies the
     *                        request,
     *                        do nothing for successful execution. {@link Errors.Collector#warn(Object, String)} and
     *                        {@link Errors.Collector#hint(Object, String)} may be used for troubleshooting/debugging purposes
     * @param request         request providing access to subject, service subject, environment and object (s)
     */
    void executePolicy(String policyStatement, Errors.Collector collector, ProviderRequest request);
}
