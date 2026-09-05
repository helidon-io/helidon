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

/**
 * Immutable typed handle to a channel owned by a {@link MessagingGraph}.
 * <p>
 * A channel does not own topology or lifecycle. Use {@link MessagingGraph#emitter(MessagingChannel)} for imperative
 * emission and close the graph to release all channel resources.
 *
 * @param <T> payload type
 */
@Api.Preview
public interface MessagingChannel<T> {
    /**
     * Channel name.
     *
     * @return channel name
     */
    String name();

    /**
     * Complete channel payload type.
     *
     * @return payload type
     */
    GenericType<T> payloadType();
}
