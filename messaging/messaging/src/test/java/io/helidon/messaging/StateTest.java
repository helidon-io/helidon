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

package io.helidon.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class StateTest {

    @Test
    void happyPath() {
        Messaging messaging = Messaging.builder().build();
        messaging.start();
        messaging.stop();
    }

    @Test
    void stopTwice() {
        Messaging messaging = Messaging.builder().build();
        messaging.start();
        messaging.stop();
        assertThrows(MessagingException.class, messaging::stop);
    }

    @Test
    void startTwice() {
        Messaging messaging = Messaging.builder().build();
        messaging.start();
        assertThrows(MessagingException.class, messaging::start);
    }

    @Test
    void startStopStart() {
        Messaging messaging = Messaging.builder().build();
        messaging.start();
        messaging.stop();
        assertThrows(MessagingException.class, messaging::start);
    }

    @Test
    void stopBeforeStart() {
        Messaging messaging = Messaging.builder().build();
        assertThrows(MessagingException.class, messaging::stop);
    }
}
