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
import java.util.function.Function;

import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

@SuppressWarnings("ALL")
class EngineSnippets {

    void snippet_1() {
        // tag::snippet_1[]
        AtomicInteger sum = new AtomicInteger();

        Multi.just("1", "2", "3", "4", "5")
                .limit(3)
                .map(Integer::parseInt)
                .forEach(sum::addAndGet);

        System.out.println("Sum: " + sum.get());

        // >Sum: 6
        // end::snippet_1[]
    }

    void snippet_2() {
        // tag::snippet_2[]
        Single.just("1")
                .map(Integer::parseInt)
                .map(i -> i + 5)
                .toStage()
                .whenComplete((i, t) -> System.out.println("Result: " + i));

        // >Result: 6
        // end::snippet_2[]
    }

    void snippet_3() {
        // tag::snippet_3[]
        // Assembly of stream, nothing is streamed yet
        Multi<String> publisherStage =
                Multi.just("foo", "bar")
                        .map(String::trim);

        Function<Multi<String>, Multi<?>> processorStage =
                upstream -> upstream.map(String::toUpperCase);

        // Execution of pre-prepared stream
        publisherStage
                .compose(processorStage)
                .map(s -> "Item received: " + s)
                .forEach(System.out::println);

        // > Item received: FOO
        // > Item received: BAR
        // end::snippet_3[]
    }

}
