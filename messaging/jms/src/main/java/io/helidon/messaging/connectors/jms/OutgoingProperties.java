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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.jms.Message;

import io.helidon.messaging.MessagingException;

class OutgoingProperties implements JmsProperties {

    private final List<JmsPropertyConsumer> msgFutures = new ArrayList<>();
    private final Map<String, Object> intermediateMap = new ConcurrentHashMap<>();

    void writeToMessage(Message m) {
        msgFutures.forEach(c -> c.accept(m));
    }

    public <T> void property(String name, T value) {
        msgFutures.add(m -> m.setObjectProperty(name, value));
        intermediateMap.put(name, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T property(final String name) {
        return (T) intermediateMap.get(name);
    }

    @FunctionalInterface
    interface JmsPropertyConsumer extends Consumer<Message> {
        @Override
        default void accept(Message m) {
            try {
                acceptThrows(m);
            } catch (final Exception e) {
                throw new MessagingException("Error when applying JMS properties.", e);
            }
        }

        void acceptThrows(Message m) throws Exception;
    }

    @Override
    public boolean propertyExists(final String name) {
        return intermediateMap.containsKey(name);
    }
}
