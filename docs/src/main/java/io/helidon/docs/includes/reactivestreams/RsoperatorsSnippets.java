/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.docs.includes.reactivestreams;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;

@SuppressWarnings("ALL")
class RsoperatorsSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        AtomicInteger sum = new AtomicInteger();

        ReactiveStreams.of("1", "2", "3", "4", "5")
                .limit(3)
                .map(Integer::parseInt)
                .forEach(sum::addAndGet)
                .run()
                .whenComplete((r, t) -> System.out.println("Sum: " + sum.get()));

        // >Sum: 6
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        // Assembly of stream, nothing is streamed yet
        PublisherBuilder<String> publisherStage =
                ReactiveStreams.of("foo", "bar")
                        .map(String::trim);

        ProcessorBuilder<String, String> processorStage =
                ReactiveStreams.<String>builder()
                        .map(String::toUpperCase);

        SubscriberBuilder<String, Void> subscriberStage =
                ReactiveStreams.<String>builder()
                        .map(s -> "Item received: " + s)
                        .forEach(System.out::println);

        // Execution of pre-prepared stream
        publisherStage
                .via(processorStage)
                .to(subscriberStage).run();

        // >Item received:FOO
        // >Item received: BAR
        // end::snippet_2[]
    }

}
