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

package io.helidon.security.abac.policy.el;

import io.helidon.config.Config;
import io.helidon.security.abac.policy.spi.PolicyExecutor;
import io.helidon.security.abac.policy.spi.PolicyExecutorService;

/**
 * Java service for {@link JavaxElPolicyExecutor} to be automatically added as a policy executor for ABAC.
 */
public class JavaxElPolicyExecutorService implements PolicyExecutorService {
    @Override
    public String configKey() {
        return "policy-javax-el";
    }

    @Override
    public PolicyExecutor instantiate(Config config) {
        return JavaxElPolicyExecutor.create(config);
    }
}
