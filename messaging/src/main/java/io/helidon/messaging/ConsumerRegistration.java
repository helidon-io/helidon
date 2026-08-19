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

import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.GenericType;
import io.helidon.service.registry.Service;

/**
 * Generated consumer registration contract.
 * <p>
 * The runtime always dispatches a {@link MessageBatch}. Generated registrations adapt that batch to the declared handler
 * signature: payload and message handlers are invoked once per item, while a batch handler is invoked once per delivery.
 */
@Service.Contract
@Api.Preview
public interface ConsumerRegistration {
    /**
     * Stable identity of this generated handler registration.
     *
     * @return handler identity
     */
    default String handlerId() {
        return getClass().getName() + "@" + Integer.toUnsignedString(System.identityHashCode(this), Character.MAX_RADIX);
    }

    /**
     * Channel name.
     *
     * @return channel name
     */
    String channel();

    /**
     * Failure policy declared for this registration's incoming logical channel.
     * The runtime reconciles declarations after applying explicit incoming-channel configuration.
     *
     * @return declared failure policy, if any
     */
    default Optional<FailurePolicy> declaredFailurePolicy() {
        return Optional.empty();
    }

    /**
     * Expected payload type.
     *
     * @return payload type
     */
    Class<?> payloadType();

    /**
     * Expected payload type including generic arguments.
     *
     * @return generic payload type
     */
    default GenericType<?> payloadGenericType() {
        return GenericType.create(payloadType());
    }

    /**
     * Expected message envelope raw type.
     *
     * @return message envelope raw type
     */
    default Class<?> envelopeType() {
        return Message.class;
    }

    /**
     * Expected message envelope type including generic arguments.
     *
     * @return generic message envelope type
     */
    default GenericType<?> envelopeGenericType() {
        return GenericType.create(envelopeType());
    }

    /**
     * Dispatch one immutable delivery batch to the generated invocation adapter.
     *
     * @param batch delivery batch
     * @throws BatchDeliveryException if delivery completes partially or its outcome is indeterminate
     * @throws RuntimeException if the consumer fails
     */
    void dispatch(MessageBatch<?> batch);
}
