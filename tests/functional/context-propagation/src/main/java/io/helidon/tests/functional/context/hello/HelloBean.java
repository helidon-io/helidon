/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.functional.context.hello;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;

import io.opentracing.SpanContext;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Timeout;

/**
 * Checks that a Helidon context is present regardless of the thread in
 * which a method is executed on.
 */
@ApplicationScoped
public class HelloBean {

    /**
     * Runs in Jersey/Helidon thread.
     *
     * @return Hello string.
     */
    public String getHello() {
        Context context = Contexts.context().get();
        Objects.requireNonNull(context);
        Objects.requireNonNull(context.get(SpanContext.class).get());
        return "Hello World";
    }

    /**
     * Runs in Hystrix thread.
     *
     * @return Hello string.
     */
    @Timeout(1000)
    public String getHelloTimeout() {
        Context context = Contexts.context().get();
        Objects.requireNonNull(context);
        Objects.requireNonNull(context.get(SpanContext.class).get());
        return "Hello World";
    }

    /**
     * Runs in Hystrix thread via FT async thread.
     *
     * @return Hello string.
     */
    @Asynchronous
    public CompletionStage<String> getHelloAsync() {
        Context context = Contexts.context().get();
        Objects.requireNonNull(context);
        Objects.requireNonNull(context.get(SpanContext.class).get());
        return CompletableFuture.completedFuture("Hello World");
    }
}
