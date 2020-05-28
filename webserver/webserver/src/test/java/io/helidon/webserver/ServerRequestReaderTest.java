/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Multi;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ServerRequestReaderTest.
 */
public class ServerRequestReaderTest {

    static class A {}

    static class B extends A {}

    static class C extends B {}

    @Test
    public void test1() throws Exception {
        Reader<B> reader = (publisher, clazz) -> Multi.create(publisher)
                .collectList()
                .toStage()
                .thenApply(byteBuffers -> new B());

        CompletionStage<? extends B> apply = reader.apply(Multi.empty());

        CompletionStage<? extends B> a = reader.apply(Multi.empty(), A.class);
        CompletionStage<? extends B> b = reader.apply(Multi.empty(), B.class);

        // this should not be possible to compile:
        //CompletionStage<? extends B> apply2 = reader.apply(Multi.empty(), C.class);
        // which is why we have the cast method
        CompletionStage<? extends C> c = reader.applyAndCast(Multi.empty(), C.class);

        assertThat(apply.toCompletableFuture().get(10, TimeUnit.SECONDS), instanceOf(B.class));
        assertThat(a.toCompletableFuture().get(10, TimeUnit.SECONDS), instanceOf(A.class));
        assertThat(b.toCompletableFuture().get(10, TimeUnit.SECONDS), instanceOf(B.class));

        try {
            B bOrC = c.toCompletableFuture().get(10, TimeUnit.SECONDS);
            fail("Should have thrown an exception.. " + bOrC);
            // if there was no explicit cast, only this would fail: Assert.assertThat(actual, instanceOf(C.class));
        } catch (ExecutionException e) {
            assertThat(e.getCause(), instanceOf(ClassCastException.class));
        }
    }
}
