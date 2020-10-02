/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

import io.helidon.microprofile.tests.junit5.AddBean;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for asynchronous invocations using {@code Asynchronous}.
 */
@AddBean(AsynchronousBean.class)
class AsynchronousTest extends FaultToleranceTest {

    @Inject
    private AsynchronousBean bean;

    @Override
    void reset() {
        bean.reset();
    }

    @Test
    void testAsync() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> future = bean.async();
        future.get();
        assertThat(bean.wasCalled(), is(true));
    }

    @Test
    void testAsyncWithFallback() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> future = bean.asyncWithFallback();
        String value = future.get();
        assertThat(bean.wasCalled(), is(true));
        assertThat(value, is("fallback"));
    }

    @Test
    void testAsyncWithFallbackFuture() {
        Future<String> future = bean.asyncWithFallbackFuture();     // fallback ignored with Future
        assertCompleteExceptionally(future, RuntimeException.class);
    }

    @Test
    void testAsyncNoGet() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> future = bean.async();
        while (!future.isDone()) {
            Thread.sleep(100);
        }
        assertThat(bean.wasCalled(), is(true));
    }

    @Test
    void testNotAsync() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> future = bean.notAsync();
        assertThat(bean.wasCalled(), is(true));
        future.get();
    }

    @Test
    void testAsyncCompletionStage() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletionStage<String> completionStage = bean.asyncCompletionStage();
        completionStage.toCompletableFuture().get();
        assertThat(bean.wasCalled(), is(true));
    }

    @Test
    void testAsyncCompletionStageWithFallback() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletionStage<String> completionStage = bean.asyncCompletionStageWithFallback();
        String value = completionStage.toCompletableFuture().get();
        assertThat(bean.wasCalled(), is(true));
        assertThat(value, is("fallback"));
    }

    @Test
    void testAsyncCompletableFuture() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> completableFuture = bean.asyncCompletableFuture();
        completableFuture.get();
        assertThat(bean.wasCalled(), is(true));
    }

    @Test
    void testAsyncCompletableFutureWithFallback() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> completableFuture = bean.asyncCompletableFutureWithFallback();
        completableFuture.get();
        assertThat(bean.wasCalled(), is(true));
    }

    @Test
    void testAsyncCompletableFutureWithFallbackFailure() throws Exception {
        assertThat(bean.wasCalled(), is(false));
        CompletableFuture<String> completableFuture = bean.asyncCompletableFutureWithFallbackFailure();
        assertThat(completableFuture.get(), is("fallback"));
        assertThat(bean.wasCalled(), is(true));
    }
}
