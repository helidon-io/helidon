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

import java.util.List;

import io.helidon.messaging.Message;
import io.helidon.messaging.MessageBatch;
import io.helidon.messaging.MessageBatchConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MessageBatchConfigPublicApiTest {
    @Test
    void buildsMessageBatchFromExternalPackage() {
        MessageBatchConfig.Builder<String> builder = MessageBatch.builder();
        MessageBatchConfig<String> config = builder.id("batch-1")
                .add(Message.create("one"))
                .add(Message.create("two"))
                .buildPrototype();

        MessageBatch<String> batch = config.build();

        assertThat(config.id(), is("batch-1"));
        assertThat(config.messages().stream().map(Message::entity).toList(), is(List.of("one", "two")));
        assertThat(batch.id(), is("batch-1"));
        assertThat(batch.payloads(), is(List.of("one", "two")));
    }

    @Test
    void builderAcceptsPretypedMessageList() {
        Message<Number> message = Message.<Number>create(42);
        List<Message<Number>> messages = List.of(message);

        MessageBatch<Number> batch = MessageBatch.<Number>builder()
                .messages(messages)
                .build();

        assertThat(batch.get(0), is(message));
        assertThat(batch.payloads(), is(List.of(42)));
    }
}
