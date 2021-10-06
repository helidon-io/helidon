/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class MultiIfEmptyTest {

    @Test
    void empty() {
        List<String> result = new ArrayList<>();
        Multi.<String>empty()
                .ifEmpty(() -> result.add("ifEmpty"))
                .onComplete(() -> result.add("onComplete"))
                .peek(result::add)
                .onError(t -> result.add("onError"))
                .ignoreElements();
        assertThat(result, contains("ifEmpty", "onComplete"));
    }

    @Test
    void multipleEmpty() {
        List<String> result = new ArrayList<>();
        Single.just(Optional.<String>empty())
                .flatMapOptional(Function.identity())
                .ifEmpty(() -> result.add("ifEmptyOptional"))
                .flatMap(s -> Single.<String>empty())
                .ifEmpty(() -> result.add("ifEmpty"))
                .onComplete(() -> result.add("onComplete"))
                .peek(result::add)
                .onError(t -> result.add("onError"))
                .ignoreElements();
        assertThat(result, contains("ifEmptyOptional", "ifEmpty", "onComplete"));
    }

    @Test
    void nonEmpty() {
        List<String> result = new ArrayList<>();
        Multi.just(1, 2, 3)
                .map(String::valueOf)
                .peek(result::add)
                .ifEmpty(() -> result.add("ifEmpty"))
                .onComplete(() -> result.add("onComplete"))
                .onError(t -> result.add("onError"))
                .ignoreElements();
        assertThat(result, contains("1", "2", "3", "onComplete"));
    }

    @Test
    void error() {
        List<String> result = new ArrayList<>();
        Multi.just(1, 2, 3)
                .flatMap(i -> {
                    if (i == 3) {
                        return Single.error(new Exception("BOOM!"));
                    } else {
                        return Single.just(String.valueOf(i));
                    }
                })
                .peek(result::add)
                .ifEmpty(() -> result.add("ifEmpty"))
                .onComplete(() -> result.add("onComplete"))
                .onError(t -> result.add("onError"))
                .ignoreElements();
        assertThat(result, contains("1", "2", "onError"));
    }

}
