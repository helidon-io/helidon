/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Callback for hooking on messages methods failures.
 */
@FunctionalInterface
public interface FailureCallback {
    /**
     * Invoked when messaging method failure occurs.
     *
     * @param method   messaging method metadata
     * @param incoming incoming message or null
     * @param e        failure cause
     */
    void accept(MessagingMethod method, Message<?> incoming, Throwable e);
}
