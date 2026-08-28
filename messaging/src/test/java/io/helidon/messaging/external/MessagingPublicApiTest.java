/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.messaging.external;

import java.lang.reflect.Method;
import java.time.Duration;

import io.helidon.messaging.ConnectorDelivery;
import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessagingEntryPoint;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MessagingPublicApiTest {
    @Test
    void publicCallbacksDeclareNoCheckedExceptions() throws NoSuchMethodException {
        assertNoDeclaredExceptions(ConnectorDelivery.class.getMethod("await"));
        assertNoDeclaredExceptions(ConnectorDelivery.class.getMethod("await", Duration.class));
        assertNoDeclaredExceptions(MessagingEntryPoint.Handler.class.getMethod("handle", Object.class, Message.class));
        assertNoDeclaredExceptions(
                MessagingEntryPoint.BatchHandler.class.getMethod("handle", Object.class, MessageBatch.class));
    }

    private static void assertNoDeclaredExceptions(Method method) {
        assertThat(method.getExceptionTypes().length, is(0));
    }
}
