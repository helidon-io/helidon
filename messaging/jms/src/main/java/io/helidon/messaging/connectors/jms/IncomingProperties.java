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

package io.helidon.messaging.connectors.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageNotWriteableRuntimeException;

import io.helidon.messaging.MessagingException;

class IncomingProperties implements JmsProperties {


    private final Message message;

    IncomingProperties(Message message) {
        this.message = message;
    }

    @Override
    public <T> void property(final String name, final T value) {
        throw new MessageNotWriteableRuntimeException("Incoming message is in read-only mode.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T property(String name) {
        try {
            return (T) message.getObjectProperty(name);
        } catch (JMSException | ClassCastException e) {
            throw new MessagingException("Error when reading JMS property " + name, e);
        }
    }

    @Override
    public boolean propertyExists(final String name) {
        try {
            return message.propertyExists(name);
        } catch (JMSException e) {
            throw new MessagingException("Error when checking existence of JMS property " + name, e);
        }
    }
}
