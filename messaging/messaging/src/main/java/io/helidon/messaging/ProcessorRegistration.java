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
import io.helidon.common.GenericType;
import io.helidon.service.registry.Service;

/**
 * Generated registration for a synchronous one-to-one message processor.
 */
@Service.Contract
@Api.Preview
public interface ProcessorRegistration extends ConsumerRegistration {
    /**
     * Target channel for the processor result.
     *
     * @return outgoing channel
     */
    String outgoingChannel();

    /**
     * Produced payload type including generic arguments.
     *
     * @return outgoing payload type
     */
    GenericType<?> outgoingPayloadGenericType();

    /**
     * Produced message envelope type including generic arguments.
     *
     * @return outgoing envelope type
     */
    GenericType<?> outgoingEnvelopeGenericType();

    /**
     * Invoke the processor adapter for one delivery and return one derived batch for
     * {@link #outgoingChannel()}.
     *
     * @param batch incoming batch
     * @return outgoing batch with preserved delivery lineage
     * @throws RuntimeException if processing fails
     */
    MessageBatch<?> process(MessageBatch<?> batch);

    @Override
    default void dispatch(MessageBatch<?> batch) {
        process(batch);
    }
}
