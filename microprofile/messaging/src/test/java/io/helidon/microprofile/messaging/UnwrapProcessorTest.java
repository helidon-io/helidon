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

package io.helidon.microprofile.messaging;


import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscriber;

public class UnwrapProcessorTest {

    public SubscriberBuilder<String, Void> testMethodSubscriberBuilderString() {
        return ReactiveStreams.<String>builder().forEach(System.out::println);
    }

    public SubscriberBuilder<Message<String>, Void> testMethodSubscriberBuilderMessage() {
        return ReactiveStreams.<Message<String>>builder().forEach(System.out::println);
    }

    public Subscriber<String> testMethodSubscriberString() {
        return ReactiveStreams.<String>builder().forEach(System.out::println).build();
    }

    public Subscriber<Message<String>> testMethodSubscriberMessage() {
        return ReactiveStreams.<Message<String>>builder().forEach(System.out::println).build();
    }

    static Stream<Method> methodSource() {
        return Stream.of(UnwrapProcessorTest.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("testMethod"));
    }

    @ParameterizedTest
    @MethodSource("methodSource")
    void innerChannelBeanTest(Method method) throws ExecutionException, InterruptedException {
        UnwrapProcessor unwrapProcessor = new UnwrapProcessor();
        unwrapProcessor.setMethod(method);
        Object unwrappedValue = unwrapProcessor.unwrap(Message.of("test"));
        if (method.getName().endsWith("Message")) {
            Assertions.assertTrue(MessageUtils.isMessageType(method));
            assertThat(unwrappedValue, instanceOf(Message.class));
        } else {
            assertThat(MessageUtils.isMessageType(method), not(true));
            assertThat(unwrappedValue, not(instanceOf(Message.class)));
        }
    }
}
