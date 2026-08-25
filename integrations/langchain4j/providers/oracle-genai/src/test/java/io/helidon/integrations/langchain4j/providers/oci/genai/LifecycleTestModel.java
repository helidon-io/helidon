/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.oci.genai;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

final class LifecycleTestModel implements ChatModel, AutoCloseable {
    private static final Map<String, Supplier<LifecycleTestModel>> PLANS = new ConcurrentHashMap<>();
    private static final AtomicInteger BUILD_COUNT = new AtomicInteger();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger closeCount = new AtomicInteger();
    private final Runnable closeAction;

    private LifecycleTestModel(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    static LifecycleTestModel create() {
        return new LifecycleTestModel(() -> { });
    }

    static LifecycleTestModel create(Runnable closeAction) {
        return new LifecycleTestModel(closeAction);
    }

    static void plan(String name, Supplier<LifecycleTestModel> modelSupplier) {
        PLANS.put(name, modelSupplier);
    }

    static void reset() {
        PLANS.clear();
        BUILD_COUNT.set(0);
    }

    static int buildCount() {
        return BUILD_COUNT.get();
    }

    static Builder builder() {
        return new Builder();
    }

    int closeCount() {
        return closeCount.get();
    }

    boolean closed() {
        return closed.get();
    }

    void assertOpen() {
        if (closed()) {
            throw new IllegalStateException("Lifecycle test model is closed.");
        }
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        assertOpen();
        return null;
    }

    @Override
    public void close() {
        closeCount.incrementAndGet();
        if (closed()) {
            return;
        }
        closeAction.run();
        closed.set(true);
    }

    static final class Builder {
        private String plan;

        private Builder() {
        }

        public Builder plan(String plan) {
            this.plan = plan;
            return this;
        }

        public LifecycleTestModel build() {
            BUILD_COUNT.incrementAndGet();
            var supplier = PLANS.get(plan);
            if (supplier == null) {
                throw new IllegalStateException("No lifecycle test model plan named '" + plan + "'.");
            }
            return supplier.get();
        }
    }
}
