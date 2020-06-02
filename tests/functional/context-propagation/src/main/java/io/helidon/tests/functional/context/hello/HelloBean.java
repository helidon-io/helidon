/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import io.helidon.metrics.RegistryFactory;

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
        Context context = Contexts.context().orElse(null);
        Objects.requireNonNull(context);
        // application scoped context
        Objects.requireNonNull(context.get(RegistryFactory.class).orElse(null));
        // request scoped context
        Objects.requireNonNull(context.get(MyMessage.class).orElse(null));
        return "Hello World";
    }

    /**
     * Runs in Hystrix thread.
     *
     * @return Hello string.
     */
    @Timeout(1000)
    public String getHelloTimeout() {
        Context context = Contexts.context().orElse(null);
        Objects.requireNonNull(context);
        // application scoped context
        Objects.requireNonNull(context.get(RegistryFactory.class).orElse(null));
        // request scoped context
        Objects.requireNonNull(context.get(MyMessage.class).orElse(null));
        return "Hello World";
    }

    /**
     * Runs in Hystrix thread via FT async thread.
     *
     * @return Hello string.
     */
    @Asynchronous
    public CompletionStage<String> getHelloAsync() {
        Context context = Contexts.context().orElse(null);
        Objects.requireNonNull(context);
        // application scoped context
        Objects.requireNonNull(context.get(RegistryFactory.class).orElse(null));
        // request scoped context
        Objects.requireNonNull(context.get(MyMessage.class).orElse(null));
        return CompletableFuture.completedFuture("Hello World");
    }
}
