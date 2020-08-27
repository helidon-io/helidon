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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.TextMessage;

import io.helidon.messaging.MessagingException;

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Message representing JMS message together with all the metadata.
 *
 * @param <T> Type of the payload.
 */
public interface JmsMessage<T> extends Message<T> {

    /**
     * The logger.
     */
    Logger LOGGER = Logger.getLogger(JmsMessage.class.getName());

    /**
     * Original JMS message received.
     *
     * @return original JMS message received
     */
    javax.jms.Message getJmsMessage();

    /**
     * Metadata about the JMS session.
     *
     * @return session metadata
     */
    SessionMetadata getSessionMetadata();

    /**
     * Create a message with the given payload.
     *
     * @param msg          JMS message to be wrapped
     * @param sessionEntry metadata about the JMS session
     * @return A message with the given payload, and an ack function
     */
    static JmsMessage<?> of(javax.jms.Message msg, SessionMetadata sessionEntry) {
        if (msg instanceof TextMessage) {
            return JmsMessage
                    .builder((TextMessage) msg)
                    .metadata(sessionEntry)
                    .build();
        } else if (msg instanceof BytesMessage) {
            return JmsMessage
                    .builder((BytesMessage) msg)
                    .metadata(sessionEntry)
                    .build();
        } else {
            throw new MessagingException("Unsupported JMS message type");
        }
    }

    /**
     * Create a message with the given payload.
     *
     * @param msg JMS message to be wrapped
     * @return A message with the given payload, and an ack function
     */
    static JmsMessage<?> of(javax.jms.Message msg) {
        if (msg instanceof TextMessage) {
            return JmsMessage.of((TextMessage) msg);
        } else if (msg instanceof BytesMessage) {
            return JmsMessage.of((BytesMessage) msg);
        } else {
            throw new MessagingException("Unsupported JMS message type");
        }
    }

    /**
     * Create a message with the given payload.
     *
     * @param msg JMS message to be wrapped
     * @return A message with the given payload, and an ack function
     */
    static JmsMessage<String> of(javax.jms.TextMessage msg) {
        return builder(msg).build();
    }

    /**
     * Create a message with the given payload.
     *
     * @param msg JMS message to be wrapped
     * @return A message with the given payload, and an ack function
     */
    static JmsMessage<InputStream> of(javax.jms.BytesMessage msg) {
        return builder(msg).build();
    }

    /**
     * Create a builder for new JmsMessage.
     *
     * @param msg JMS message to be wrapped
     * @return JMS message builder
     */
    static BytesMessageBuilder builder(final javax.jms.BytesMessage msg) {
        return new BytesMessageBuilder(msg);
    }

    /**
     * Create a builder for new JmsMessage.
     *
     * @param msg JMS message to be wrapped
     * @return JMS message builder
     */
    static TextMessageBuilder builder(final javax.jms.TextMessage msg) {
        return new TextMessageBuilder(msg);
    }

    class TextMessageBuilder implements io.helidon.common.Builder<JmsMessage<String>> {

        private final javax.jms.TextMessage msg;
        private SessionMetadata sharedSessionEntry;

        public TextMessageBuilder(final javax.jms.TextMessage msg) {
            this.msg = msg;
        }

        public TextMessageBuilder metadata(final SessionMetadata sharedSessionEntry) {
            this.sharedSessionEntry = sharedSessionEntry;
            return this;
        }

        @Override
        public JmsMessage<String> build() {
            return new JmsMessage<>() {

                @Override
                public javax.jms.Message getJmsMessage() {
                    return msg;
                }

                @Override
                public SessionMetadata getSessionMetadata() {
                    return sharedSessionEntry;
                }

                @Override
                public String getPayload() {
                    try {
                        return msg.getText();
                    } catch (JMSException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public CompletionStage<Void> ack() {
                    return CompletableFuture.runAsync(() -> {
                        try {
                            this.getJmsMessage().acknowledge();
                        } catch (JMSException e) {
                            LOGGER.log(Level.SEVERE, e, () -> "Error during acknowledgement of JMS message");
                        }
                    });
                }
            };
        }

        @Override
        public JmsMessage<String> get() {
            return this.build();
        }
    }

    class BytesMessageBuilder implements io.helidon.common.Builder<JmsMessage<InputStream>> {

        private final javax.jms.BytesMessage msg;
        private SessionMetadata sharedSessionEntry;

        public BytesMessageBuilder(final javax.jms.BytesMessage msg) {
            this.msg = msg;
        }

        public BytesMessageBuilder metadata(final SessionMetadata sharedSessionEntry) {
            this.sharedSessionEntry = sharedSessionEntry;
            return this;
        }

        @Override
        public JmsMessage<InputStream> build() {
            return new JmsMessage<>() {

                @Override
                public javax.jms.Message getJmsMessage() {
                    return msg;
                }

                @Override
                public SessionMetadata getSessionMetadata() {
                    return sharedSessionEntry;
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

                @Override
                public CompletionStage<Void> ack() {
                    return CompletableFuture.runAsync(() -> {
                        try {
                            this.getJmsMessage().acknowledge();
                        } catch (JMSException e) {
                            LOGGER.log(Level.SEVERE, e, () -> "Error during acknowledgement of JMS message");
                        }
                    });
                }
            };
        }

        @Override
        public JmsMessage<InputStream> get() {
            return this.build();
        }
    }
}
