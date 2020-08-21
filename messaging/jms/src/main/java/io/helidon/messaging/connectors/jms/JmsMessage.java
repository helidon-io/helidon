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
 *
 */

package io.helidon.messaging.connectors.jms;


import java.io.IOException;
import java.io.InputStream;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.TextMessage;

import org.eclipse.microprofile.reactive.messaging.Message;

import io.helidon.messaging.MessagingException;

interface JmsMessage<T> extends Message<T> {

    javax.jms.Message getJmsMessage();

    static JmsMessage<?> of(javax.jms.Message msg) {
        if (msg instanceof TextMessage) {
            return JmsMessage.of((TextMessage) msg);
        } else if (msg instanceof BytesMessage) {
            return JmsMessage.of((BytesMessage) msg);
        } else {
            throw new MessagingException("Unsupported JMS message type");
        }
    }

    static JmsMessage<String> of(javax.jms.TextMessage msg) {
        return new JmsMessage<>() {

            @Override
            public javax.jms.TextMessage getJmsMessage() {
                return msg;
            }

            @Override
            public String getPayload() {
                try {
                    return msg.getText();
                } catch (JMSException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    static JmsMessage<InputStream> of(javax.jms.BytesMessage msg) {
        return new JmsMessage<>() {

            @Override
            public javax.jms.BytesMessage getJmsMessage() {
                return msg;
            }

            @Override
            public InputStream getPayload() {
                return new InputStream() {
                    @Override
                    public int read() throws IOException {
                        try {
                            return msg.readByte();
                        } catch (MessageEOFException e) {
                            return -1;
                        } catch (JMSException e) {
                            throw new IOException(e);
                        }
                    }
                };
            }
        };
    }
}
