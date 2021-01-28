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

package io.helidon.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber wrapper used to invoke pre-process and post-process logic.
 *
 * @param <T> type of the subscriber value
 */
class ProxySubscriber<T> implements Subscriber<T> {

    private final IncomingMethod method;
    private final Subscriber<T> originalSubscriber;

    ProxySubscriber(IncomingMethod method, Subscriber<T> originalSubscriber) {
        this.method = method;
        this.originalSubscriber = originalSubscriber;
    }

    @Override
    public void onSubscribe(Subscription s) {
        originalSubscriber.onSubscribe(s);
    }

    @Override
    public void onNext(T o) {
        method.beforeInvoke(o);
        originalSubscriber.onNext(preProcess(o));
        method.afterInvoke(o, null);
        postProcess(o);
    }

    @Override
    public void onError(Throwable t) {
        originalSubscriber.onError(t);
    }

    @Override
    public void onComplete() {
        originalSubscriber.onComplete();
    }

    private T preProcess(T incomingValue) {
        Message<?> incomingMessage = (Message<?>) incomingValue;
        if (method.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING)) {
            incomingMessage.ack();
        }
        return incomingValue;
    }

    private void postProcess(T incomingValue) {
        if (method.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING)
                && incomingValue instanceof Message) {
            Message<?> incomingMessage = (Message<?>) incomingValue;
            incomingMessage.ack();
        }
    }
}
