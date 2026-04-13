/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.scheduling;

import java.time.Duration;

/**
 * Specific method invocation metadata for scheduled task.
 */
public interface FixedRateInvocation extends Invocation {
    /**
     * Delay the first invocation by the specified time.
     *
     * @return delay of the first invocation
     */
    Duration delayBy();

    /**
     * Interval between two invocations.
     *
     * @return interval between invocations
     */
    Duration interval();
}
