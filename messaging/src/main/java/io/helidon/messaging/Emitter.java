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
import io.helidon.service.registry.Service;

/**
 * Synchronous emitter for a named messaging channel.
 * <p>
 * Every emission enters the runtime as a {@link MessageBatch}. The payload and message methods are singleton-batch
 * conveniences. A method returns only after every required channel output completes successfully. Outputs are invoked
 * sequentially and the first failure is propagated to the caller without invoking remaining outputs. An earlier output
 * may have completed before a later output fails, so retrying can deliver the same message more than once.
 * <p>
 * A directly created child thread that inherits thread-local state retains the current delivery ancestry until the
 * originating handler returns. Its emissions use nested admission, and emissions back into any channel already on the
 * active delivery path are rejected. Existing executor workers, common-pool tasks, and threads that disable inheritable
 * state cannot retain that ancestry; a handler must not wait for their emission to a channel on its active delivery
 * path.
 *
 * @param <T> payload type
 */
@Service.Contract
@Api.Preview
public interface Emitter<T> {
    /**
     * Emit a payload-only message.
     * <p>
     * A successful return means end-to-end delivery to all required outputs completed. A thrown exception means
     * delivery failed or its outcome is indeterminate.
     *
     * @param entity payload
     * @throws MessagingException if the target channel does not exist
     * @throws RuntimeException if a handler or outgoing connector fails
     */
    default void emit(T entity) {
        emitBatch(MessageBatch.create(Message.create(entity)));
    }

    /**
     * Emit a message with metadata.
     * <p>
     * A successful return means end-to-end delivery to all required outputs completed. A thrown exception means
     * delivery failed or its outcome is indeterminate.
     *
     * @param message message
     * @throws MessagingException if the target channel does not exist
     * @throws RuntimeException if a handler or outgoing connector fails
     */
    default void emitMessage(Message<? extends T> message) {
        emitBatch(MessageBatch.create(message));
    }

    /**
     * Emit a batch of messages.
     *
     * @param batch immutable message batch
     * @throws MessagingException if the target channel does not exist
     * @throws BatchDeliveryException if delivery completes partially or its outcome is indeterminate
     */
    void emitBatch(MessageBatch<? extends T> batch);
}
