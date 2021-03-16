/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * JMS message to be sent with JMS connector.
 *
 * @param <PAYLOAD> payload type
 */
public interface OutgoingJmsMessage<PAYLOAD> extends Message<PAYLOAD> {

    /**
     * Register action to be invoked after {@link javax.jms.Message} is created.
     *
     * @param postProcessor for accessing {@link javax.jms.Message}
     */
    void addPostProcessor(PostProcessor postProcessor);

    @FunctionalInterface
    interface PostProcessor {
        void accept(javax.jms.Message m) throws JMSException;
    }
}

