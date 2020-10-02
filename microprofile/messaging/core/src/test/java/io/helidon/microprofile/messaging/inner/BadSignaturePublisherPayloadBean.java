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

package io.helidon.microprofile.messaging.inner;

import io.helidon.microprofile.messaging.AssertThrowException;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.Publisher;

import javax.enterprise.context.ApplicationScoped;

/**
 * This test is modified version of official tck test in version 1.0
 * https://github.com/eclipse/microprofile-reactive-messaging
 */
@ApplicationScoped
//TODO: Uncomment when TCK issue is solved https://github.com/eclipse/microprofile-reactive-messaging/issues/79
//@AssertThrowException(Exception.class)
public class BadSignaturePublisherPayloadBean extends AbstractShapeTestBean {

    @Outgoing("string-payload")
    public Publisher<Message<String>> sourceForStringPayload() {
        return ReactiveStreams.fromIterable(TEST_DATA).map(Message::of).buildRs();
    }

    @Incoming("string-payload")
    public String consumePayloadsAndReturnSomething(String payload) {
        testLatch.countDown();
        return payload;
    }

}
