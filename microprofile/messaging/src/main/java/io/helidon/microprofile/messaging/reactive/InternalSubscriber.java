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

package io.helidon.microprofile.messaging.reactive;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.microprofile.messaging.MessageUtils;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.Method;
import java.util.UUID;

public class InternalSubscriber implements Subscriber<Object> {

    private Subscription subscription;
    private Long chunkSize = 5L;
    private Long chunkPosition = 0L;
    private Method method;
    private Object beanInstance;

    public InternalSubscriber(Method method, Object beanInstance) {
        this.method = method;
        this.beanInstance = beanInstance;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        //First chunk request
        subscription.request(chunkSize);
    }

    @Override
    public void onNext(Object message) {
        try {
            Class<?> paramType = this.method.getParameterTypes()[0];

            Context parentContext = Context.create();
            Context context = Context
                    .builder()
                    .parent(parentContext)
                    .id(parentContext.id() + ":message-" + UUID.randomUUID().toString())
                    .build();
            Contexts.runInContext(context, () -> this.method.invoke(this.beanInstance, MessageUtils.unwrap(message, paramType)));
            incrementAndCheckChunkPosition();
        } catch (Exception e) {
            //Notify publisher to stop sending
            subscription.cancel();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        //TODO: Propagate error
        throw new RuntimeException(t);
    }

    @Override
    public void onComplete() {

    }

    private void incrementAndCheckChunkPosition() {
        chunkPosition++;
        if (chunkPosition >= chunkSize) {
            chunkPosition = 0L;
            subscription.request(chunkSize);
        }
    }
}
