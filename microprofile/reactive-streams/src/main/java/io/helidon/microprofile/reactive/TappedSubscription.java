/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.util.Optional;
import java.util.function.Consumer;

import org.reactivestreams.Subscription;

public class TappedSubscription implements Subscription {

    private Optional<Consumer<Long>> onRequest = Optional.empty();
    private Optional<Runnable> onCancel = Optional.empty();

    private TappedSubscription() {
    }

    public static TappedSubscription create() {
        return new TappedSubscription();
    }

    public TappedSubscription onRequest(Consumer<Long> onRequest) {
        this.onRequest = Optional.of(onRequest);
        return this;
    }

    public TappedSubscription onCancel(Runnable onCancel) {
        this.onCancel = Optional.of(onCancel);
        return this;
    }

    @Override
    public void request(long n) {
        this.onRequest.ifPresent(c -> c.accept(n));
    }

    @Override
    public void cancel() {
        this.onCancel.ifPresent(Runnable::run);
    }
}
