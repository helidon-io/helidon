/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.abac.policy;

import io.helidon.common.Errors;
import io.helidon.security.ProviderRequest;

/**
 * Example policy class for {@link PolicyValidatorTest}.
 */
public class PolicyClass {
    public static void isAuthenticated(Errors.Collector collector, ProviderRequest request) {
        if (!request.securityContext().isAuthenticated()) {
            collector.fatal("User is not authenticated");
        }
    }

    public static void isObjectPresent(Errors.Collector collector, ProviderRequest request) {
        if (!request.getObject().isPresent()) {
            collector.fatal("Object is not present");
        }
    }
}
