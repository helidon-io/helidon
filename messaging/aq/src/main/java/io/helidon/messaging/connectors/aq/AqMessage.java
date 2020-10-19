/*
 * Copyright (c)  2020 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.aq;

import java.sql.Connection;
import java.util.concurrent.Executor;

import io.helidon.messaging.connectors.jms.JmsMessage;
import io.helidon.messaging.connectors.jms.SessionMetadata;

import oracle.jms.AQjmsMessage;

/**
 * Message representing AQ JMS message together with all the metadata.
 *
 * @param <T> Type of the payload.
 */
public interface AqMessage<T> extends JmsMessage<T> {

    /**
     * Return DB connection used for receiving this message.
     *
     * @return java.sql.Connection
     */
    Connection getDbConnection();

    /**
     * Create from AQjmsMessage.
     *
     * @param msg             Oracle AQ JMS message to be wrapped
     * @param executor        Executor used for invoking ack
     * @param sessionMetadata metadata about the JMS session
     * @param <T>             payload type
     * @return A message with the given payload, and an ack function
     */
    static <T> AqMessage<T> of(AQjmsMessage msg, Executor executor, SessionMetadata sessionMetadata) {
        return new AqMessageImpl<>(msg, executor, sessionMetadata);
    }
}
