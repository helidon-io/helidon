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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.reactive.Flow;

public class FindFirstSubscriber<Object> implements Flow.Subscriber<Object> {
    private Flow.Subscription subscription;
    private CompletableFuture<Object> completionStage = new CompletableFuture<>();
    private Optional<Object> firstItem = Optional.empty();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(1);
    }


    @Override
    @SuppressWarnings("unchecked")
    public void onNext(Object item) {
        subscription.cancel();
        Object optItem = (Object) Optional.of(item);
        completionStage.complete(optItem);
    }

    @Override
    public void onError(Throwable throwable) {
        ExceptionUtils.throwUncheckedException(throwable);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onComplete() {
        Object optItem = (Object) Optional.empty();
        completionStage.complete(optItem);
    }


    public CompletionStage<Object> getCompletion() {
        return completionStage;
    }
}
