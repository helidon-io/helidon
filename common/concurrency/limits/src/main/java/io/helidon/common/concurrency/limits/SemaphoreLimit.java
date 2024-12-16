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

package io.helidon.common.concurrency.limits;

import java.util.concurrent.Semaphore;

/**
 * The {@link io.helidon.common.concurrency.limits.Limit} is backed by a semaphore, and this provides
 * direct access to the semaphore.
 * Note that this usage may bypass calculation of limits if the semaphore is used directly.
 * This is for backward compatibility only, and will be removed.
 *
 * @deprecated DO NOT USE except for backward compatibility with semaphore based handling
 */
@Deprecated(since = "4.2.0", forRemoval = true)
public interface SemaphoreLimit {
    /**
     * Underlying semaphore of this limit.
     *
     * @return the semaphore instance
     * @deprecated this only exists for backward compatibility of Helidon WebServer and will be removed
     */
    @Deprecated(forRemoval = true, since = "4.2.0")
    Semaphore semaphore();
}
