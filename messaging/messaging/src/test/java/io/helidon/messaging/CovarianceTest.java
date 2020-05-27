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

package io.helidon.messaging;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.helidon.common.reactive.Multi;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.junit.jupiter.api.Test;

public class CovarianceTest {
    @Test
    void flowPublisherWithWrapper() {
        List<String> testValues = List.of("test1", "test2", "test3", "test4");

        LatchedTestData<CoolerString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolerString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolerString> channel1 = Channel.create();
        Channel<CoolerString> channel2 = Channel.create();
        Channel<CharSequence> channel3 = Channel.create();
        Channel<CoolString> channel4 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, Multi.just(testData.expected.get(0)),
                        (Object s) -> Message.of((CoolerString) s))
                .publisher(channel2, Multi.just(testData.expected.get(1)),
                        (CharSequence s) -> Message.of((CoolerString) s))
                .publisher(channel3, Multi.just(testData.expected.get(2)),
                        Message::of)
                .publisher(channel4, Multi.just((CoolerString) testData.expected.get(3)),
                        (Object s) -> Message.of((CoolerString) s))
                .listener(channel1, testData::add)
                .listener(channel2, testData::add)
                .listener(channel3, charSequence -> testData.add((CoolerString) charSequence))
                .listener(channel4, coolString -> testData.add((CoolerString) coolString))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    void publisherWithWrapper() {
        List<String> testValues = List.of("test1", "test2", "test3", "test4");

        LatchedTestData<CoolerString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolerString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolerString> channel1 = Channel.create();
        Channel<CoolerString> channel2 = Channel.create();
        Channel<CharSequence> channel3 = Channel.create();
        Channel<CoolString> channel4 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).buildRs(),
                        (Object s) -> Message.of((CoolerString) s))
                .publisher(channel2, ReactiveStreams.of(testData.expected.get(1)).buildRs(),
                        (CharSequence s) -> Message.of((CoolerString) s))
                .publisher(channel3, ReactiveStreams.of(testData.expected.get(2)).buildRs(),
                        Message::of)
                .publisher(channel4, ReactiveStreams.of((CoolerString) testData.expected.get(3)).buildRs(),
                        (Object s) -> Message.of((CoolerString) s))
                .listener(channel1, testData::add)
                .listener(channel2, testData::add)
                .listener(channel3, charSequence -> testData.add((CoolerString) charSequence))
                .listener(channel4, coolString -> testData.add((CoolerString) coolString))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    void publisherBuilder() {
        List<String> testValues = List.of("test1", "test2");

        LatchedTestData<CoolestString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolestString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolerString> channel1 = Channel.create();
        Channel<CoolerString> channel2 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .publisher(channel2, ReactiveStreams.of((CoolestString) testData.expected.get(1)).map(Message::of))
                .listener(channel1, (CharSequence charSequence) -> testData.add((CoolestString) charSequence))
                .listener(channel2, coolString -> testData.add((CoolestString) coolString))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }


    @Test
    void processorFunction() {
        List<String> testValues = List.of("test1");

        LatchedTestData<CoolestString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolestString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolestString> channel1 = Channel.create();
        Channel<CoolerString> channel2 = Channel.create();
        Channel<CoolString> channel3 = Channel.create();
        Channel<CoolerString> channel4 = Channel.create();
        Channel<CoolestString> channel5 = Channel.create();
        Channel<CoolString> channel6 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .processor(channel1, channel2, s -> s)
                .processor(channel2, channel3, s -> s)
                .processor(channel3, channel4, (CoolString s) -> (CoolerString) s)
                .processor(channel4, channel5, s -> (CoolestString) s)
                .processor(channel5, channel6, s -> s)
                .listener(channel6, coolString -> testData.add((CoolestString) coolString))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    void processorBuilder() {
        List<String> testValues = List.of("test1");

        LatchedTestData<CoolestString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolestString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolestString> channel1 = Channel.create();
        Channel<CoolerString> channel2 = Channel.create();
        Channel<CoolString> channel3 = Channel.create();
        Channel<CoolerString> channel4 = Channel.create();
        Channel<CoolestString> channel5 = Channel.create();
        Channel<CoolString> channel6 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .processor(channel1, channel2, ReactiveStreams.<Message<CoolestString>>builder()
                        .map(o -> Message.of((CoolerString) o.getPayload())))
                .processor(channel2, channel3, ReactiveStreams.<Message<CoolestString>>builder()
                        .map(Function.identity()))
                .processor(channel3, channel4, ReactiveStreams.<Message<CoolString>>builder()
                        .map(s -> Message.of((CoolestString) s.getPayload())))
                .processor(channel4, channel5, ReactiveStreams.<Message<CoolerString>>builder()
                        .map(s -> Message.of((CoolestString) s.getPayload())))
                .processor(channel5, channel6, s -> s)
                .listener(channel6, coolString -> testData.add((CoolestString) coolString))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    void processor() {
        List<String> testValues = List.of("test1");

        LatchedTestData<CoolestString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolestString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolestString> channel1 = Channel.create();
        Channel<CoolerString> channel2 = Channel.create();
        Channel<CoolString> channel3 = Channel.create();
        Channel<CoolerString> channel4 = Channel.create();
        Channel<CoolestString> channel5 = Channel.create();
        Channel<CoolString> channel6 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .processor(channel1, channel2, ReactiveStreams.<Message<CoolestString>>builder()
                        .map(o -> Message.of((CoolerString) o.getPayload())).buildRs())
                .processor(channel2, channel3, ReactiveStreams.<Message<CoolestString>>builder()
                        .map(Function.identity()).buildRs())
                .processor(channel3, channel4, ReactiveStreams.<Message<CoolString>>builder()
                        .map(s -> Message.of((CoolestString) s.getPayload())).buildRs())
                .processor(channel4, channel5, ReactiveStreams.<Message<CoolerString>>builder()
                        .map(s -> Message.of((CoolestString) s.getPayload())).buildRs())
                .processor(channel5, channel6, s -> s)
                .listener(channel6, coolString -> testData.add((CoolestString) coolString))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    @SuppressWarnings("unchecked")
    void multiSubscriber() {
        List<String> testValues = List.of("test1");

        LatchedTestData<CoolerString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolerString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolerString> channel1 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .subscriber(channel1, multi -> multi
                        .map((Object m) -> (CoolerString) ((Message<CoolString>) m).getPayload())
                        .forEach((CoolString m) -> testData.add((CoolerString) m))
                )
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    void subscriberBuilder() {
        List<String> testValues = List.of("test1");

        LatchedTestData<CoolerString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolerString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolerString> channel1 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .subscriber(channel1, ReactiveStreams.<Message<CoolerString>>builder()
                        .map(Message::getPayload)
                        .forEach(testData::add))
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    @Test
    void subscriber() {
        List<String> testValues = List.of("test1");

        LatchedTestData<CoolerString> testData = new LatchedTestData<>(
                testValues.stream()
                        .map(CoolerString::new)
                        .collect(Collectors.toList())
        );

        Channel<CoolerString> channel1 = Channel.create();

        Messaging messaging = Messaging.builder()
                .publisher(channel1, ReactiveStreams.of(testData.expected.get(0)).map(Message::of))
                .subscriber(channel1, ReactiveStreams.<Message<CoolerString>>builder()
                        .map(Message::getPayload)
                        .forEach(testData::add)
                        .build())
                .build()
                .start();

        testData.assertEqualsAnyOrder();

        messaging.stop();
    }

    static class CoolestString extends CoolerString {
        public CoolestString(final String content) {
            super(content);
        }
    }

    static class CoolerString extends CoolString {
        public CoolerString(final String content) {
            super(content);
        }
    }

    static class CoolString implements CharSequence {

        private final String content;

        public CoolString(String content) {
            this.content = content;
        }

        @Override
        public int length() {
            return content.length();
        }

        @Override
        public char charAt(final int index) {
            return content.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return content.subSequence(start, end);
        }

        @Override
        public String toString() {
            return content;
        }
    }
}
