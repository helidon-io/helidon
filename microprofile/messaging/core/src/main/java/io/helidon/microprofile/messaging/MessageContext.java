/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.reactive.messaging.Message;

public class MessageContext extends ConcurrentHashMap<String, Object> {

    private static final long serialVersionUID = -5054139168502426532L;
    private static final Map<Message<?>, MessageContext> CONTEXT_MAP = new WeakHashMap<>();

    public static synchronized MessageContext lookup(Message<?> msg) {
        return CONTEXT_MAP.computeIfAbsent(msg, m -> new MessageContext());
    }

    public static synchronized MessageContext copy(Message<?> from, Message<?> to) {
        return set(to, lookup(from));
    }

    public static synchronized MessageContext set(Message<?> msg, MessageContext newCtx) {
        return CONTEXT_MAP.compute(msg, (message, oldCtx) -> {
            if (oldCtx != null) {
                oldCtx.putAll(newCtx);
                return oldCtx;
            }
            return newCtx;
        });
    }

    private MessageContext() {
        super();
    }
}
