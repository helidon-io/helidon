/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Message;

class AckCtx {
    private final AbstractMessagingMethod m;
    private final Message<?> msg;
    private final AtomicBoolean acked = new AtomicBoolean();

    private AckCtx(AbstractMessagingMethod m, Message<?> msg) {
        this.m = m;
        this.msg = msg;
    }

    static AckCtx create(AbstractMessagingMethod m, Message<?> msg) {
        return new AckCtx(m, msg);
    }

    CompletionStage<Void> preAck() {
        if (m.getAckStrategy().equals(Acknowledgment.Strategy.PRE_PROCESSING) && !acked.getAndSet(true)) {
            return msg.ack();
        }
        return CompletableFuture.completedStage(null);
    }

    CompletionStage<Void> postNack(Throwable t) {
        if (m.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING) && !acked.getAndSet(true)) {
            return msg.nack(t);
        }
        return CompletableFuture.completedStage(null);
    }

    CompletionStage<Void> postAck() {
        if (m.getAckStrategy().equals(Acknowledgment.Strategy.POST_PROCESSING) && !acked.getAndSet(true)) {
            return msg.ack();
        }
        return CompletableFuture.completedStage(null);
    }
}
