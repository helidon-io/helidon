/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.microprofile.reactive.messaging.Message;

public class MessageUtils {
    public static Object unwrap(Object value, Class<?> type) {
        if (type.equals(Message.class)) {
            if (value instanceof Message) {
                return type.cast(value);
            } else {
                return Message.of(value);
            }
        } else {
            if (value instanceof Message) {
                return type.cast(((Message) value).getPayload());
            } else if (type.isInstance(value)) {
                return type.cast(value);
            } else {
                throw new RuntimeException("Type mismatch " + value.getClass() + "cant be cast to " + type);
            }
        }
    }
}
