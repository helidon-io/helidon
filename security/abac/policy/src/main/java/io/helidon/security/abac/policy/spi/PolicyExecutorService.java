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

import io.helidon.config.Config;
import io.helidon.security.abac.policy.PolicyValidator;

/**
 * Java service for {@link PolicyValidator} ABAC validator.
 */
public interface PolicyExecutorService {
    /**
     * Configuration key that is expected by this policy validator service.
     * This is obtained from policy-validator area in configuration. Example:
     * <pre>
     * security.providers:
     *  - abac
     *    policy-validator:
     *      my-custom-policy-engine:
     *        some-key: "some value"
     *        another-key: "another value"
     * </pre>
     *
     * @return configuration key (e.g. "my-custom-policy-engine" from example above)
     */
    String configKey();

    /**
     * Create a new instance of a {@link PolicyExecutor} based on the configuration provider.
     * Another option is to use a builder directly with {@link PolicyValidator.Builder#addExecutor}
     *
     * @param config configuration as located on {@link #configKey()}.
     * @return new executor instance to be used by {@link PolicyValidator} to evaluate policy
     * statements
     */
    PolicyExecutor instantiate(Config config);
}
