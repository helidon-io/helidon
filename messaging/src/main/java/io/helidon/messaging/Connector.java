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

import io.helidon.common.Api;

/**
 * Lifecycle owned by one messaging-graph binding.
 * <p>
 * Implementations are one-shot and must make forced and normal close idempotent. A closed connector cannot be
 * restarted or reused by another binding.
 */
@Api.Preview
public interface Connector extends AutoCloseable {
    /**
     * Force prompt shutdown without waiting for normal delivery settlement.
     * <p>
     * This method must promptly unblock every in-progress lifecycle, source, or transport operation owned by this
     * connector, including preparation, startup, readiness, admission, and polling as applicable. It may be invoked
     * concurrently with those operations and must be idempotent.
     */
    void forceClose();

    /**
     * Close this binding after its delivery work has drained.
     */
    @Override
    void close();
}
