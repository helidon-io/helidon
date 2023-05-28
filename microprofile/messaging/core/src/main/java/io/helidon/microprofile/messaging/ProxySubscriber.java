/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.messaging;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber wrapper used to invoke pre-process and post-process logic.
 *
 * @param <T> type of the subscriber value
 */
class ProxySubscriber implements Subscriber<Object> {

    private static final Logger LOGGER = Logger.getLogger(ProxySubscriber.class.getName());

    private final IncomingMethod method;
    private final Subscriber<Object> originalSubscriber;
    private final boolean unwrap;

    private ProxySubscriber(IncomingMethod method, Subscriber<Object> originalSubscriber, boolean unwrap) {
        this.method = method;
        this.originalSubscriber = originalSubscriber;
        this.unwrap = unwrap;
    }

    static ProxySubscriber wrapped(IncomingMethod method, Subscriber<Object> originalSubscriber) {
        return new ProxySubscriber(method, originalSubscriber, false);
    }

    static ProxySubscriber unwrapped(IncomingMethod method, Subscriber<Object> originalSubscriber) {
        return new ProxySubscriber(method, originalSubscriber, true);
    }

    @Override
    public void onSubscribe(Subscription s) {
        originalSubscriber.onSubscribe(s);
    }

    @Override
    public void onNext(Object item) {
        Message<?> msg = (Message<?>) item;
        AckCtx ackCtx = AckCtx.create(method, msg);
        ackCtx.preAck();
        try {
            if (unwrap) {
                originalSubscriber.onNext(MessageUtils.unwrap(msg));
            } else {
                originalSubscriber.onNext(msg);
            }
            ackCtx.postAck();
        } catch (Throwable t) {
            ackCtx.postNack(t);
            LOGGER.log(Level.SEVERE, "Error thrown by subscriber on channel " + method.getIncomingChannelName(), t);
        }
    }

    @Override
    public void onError(Throwable t) {
        originalSubscriber.onError(t);
    }

    @Override
    public void onComplete() {
        originalSubscriber.onComplete();
    }
}
