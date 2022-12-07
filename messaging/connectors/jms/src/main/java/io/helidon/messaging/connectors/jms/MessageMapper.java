/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.messaging.connectors.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;

/**
 * Mapper used for translating reactive messaging message to JMS message.
 */
@FunctionalInterface
public interface MessageMapper {
    /**
     * Convert messaging message to JMS message.
     *
     * @param s JMS session
     * @param m Reactive messaging message to be converted
     * @return JMS message
     * @throws JMSException
     */
    Message apply(Session s, org.eclipse.microprofile.reactive.messaging.Message<?> m) throws JMSException;
}
