/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

package io.helidon.common;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link Builder}.
 */
class BuilderTest {
    @Test
    void test1() {
        String message = "Hello world";

        Result1 r = Result1.builder()
                .message(message)
                .build();

        assertThat(r.getMessage(), is(message));
    }

    @Test
    void test2() {
        String name = "Hello worlds";

        Result2 r = Result2.builder()
                .name(name)
                .build();

        assertThat(r.getName(), is(name));
    }

    private static class Result1 {
        private final String message;

        private Result1(Builder1 builder) {
            this.message = builder.message;
        }

        static Builder1 builder() {
            return new Builder1();
        }

        String getMessage() {
            return message;
        }
    }

    private static class Result2 {
        private final String name;

        private Result2(Builder2 builder) {
            this.name = builder.name;
        }

        static Builder2 builder() {
            return new Builder2();
        }

        String getName() {
            return name;
        }
    }

    private static class Builder1 implements Builder<Result1> {
        private String message;

        private Builder1() {
        }

        Builder1 message(String message) {
            this.message = message;
            return this;
        }

        @Override
        public Result1 build() {
            return new Result1(this);
        }
    }

    private static class Builder2 implements Builder<Result2> {
        private String name;

        private Builder2() {
        }

        Builder2 name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public Result2 build() {
            return new Result2(this);
        }
    }
}
