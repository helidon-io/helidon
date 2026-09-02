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

package io.helidon.messaging;

import java.time.Duration;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;

/**
 * Retry configuration for incoming delivery failures.
 */
@Api.Preview
@Prototype.Blueprint(decorator = RetryConfigBuilderDecorator.class)
@Prototype.Configured
interface RetryConfigBlueprint {
    /**
     * Positive delay before retrying a failed delivery.
     *
     * @return retry delay
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration delay();

    /**
     * Maximum total delivery attempts, including the initial attempt; zero means unlimited attempts.
     *
     * @return maximum delivery attempts, or zero for unlimited attempts
     */
    @Option.Configured
    @Option.DefaultInt(0)
    int maxAttempts();
}
