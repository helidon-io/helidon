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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CompletableQueueTest {

    private static Stream<Arguments> provideAllCombinations() {
        List<String> result = new ArrayList<>();
        int start = 0;
        int end = 3;
        for (int i = start; i <= end; i++) {
            for (int j = start; j <= end; j++) {
                for (int k = start; k <= end; k++) {
                    for (int l = start; l <= end; l++) {
                        if (Stream.of(i, j, k, l).distinct().count() == 4) {
                            result.add("" + i + j + k + l);
                        }
                    }
                }
            }
        }
        return result.stream().map(Arguments::of);
    }


    @ParameterizedTest
    @MethodSource("provideAllCombinations")
    void orderTest(String arg) {
        List<Integer> order = arg
                .chars()
                .map(Character::getNumericValue)
                .boxed()
                .collect(Collectors.toList());

        List<Integer> resultList = new ArrayList<>(arg.length());
        CompletableQueue<Integer> queue = CompletableQueue.create((o, throwable) -> resultList.add(o.getValue()));
        List<CompletableFuture<Integer>> futures = IntStream
                .rangeClosed(1, arg.length())
                .mapToObj(i -> new CompletableFuture<Integer>())
                .collect(Collectors.toList());

        futures.forEach(queue::add);

        order.forEach(i -> futures.get(i).complete(i));

        assertThat(resultList, is(IntStream
                .range(0, arg.length())
                .boxed()
                .collect(Collectors.toList())));
    }
}
