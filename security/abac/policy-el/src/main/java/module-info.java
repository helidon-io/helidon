/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

/**
 * Policy attribute validator.
 */
module io.helidon.security.abac.policy.el {

    requires io.helidon.security.util;
    requires java.desktop;

    // expected to be provided by the actual EL implementation
    requires static jakarta.el;

    requires transitive io.helidon.common;
    requires transitive io.helidon.security.abac.policy;
    requires transitive io.helidon.security.providers.abac;
    requires transitive io.helidon.security;

    exports io.helidon.security.abac.policy.el;

    provides io.helidon.security.abac.policy.spi.PolicyExecutorService
            with io.helidon.security.abac.policy.el.JavaxElPolicyExecutorService;

}
