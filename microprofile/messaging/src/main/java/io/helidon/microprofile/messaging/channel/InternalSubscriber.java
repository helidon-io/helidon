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

package io.helidon.microprofile.messaging.channel;

import java.lang.reflect.Method;
import java.util.UUID;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.microprofile.messaging.MessagingStreamException;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Publisher calling underlined messaging method for every received item.
 */
class InternalSubscriber implements Subscriber<Object> {

    private Subscription subscription;
    private Method method;
    private Object beanInstance;

    InternalSubscriber(Method method, Object beanInstance) {
        this.method = method;
        this.beanInstance = beanInstance;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        // request one by one
        subscription.request(1);
    }

    @Override
    public void onNext(Object message) {
        try {
            Class<?> paramType = this.method.getParameterTypes()[0];

            Context parentContext = Context.create();
            Context context = Context
                    .builder()
                    .parent(parentContext)
                    .id(String.format("%s:message-%s", parentContext.id(), UUID.randomUUID().toString()))
                    .build();
            Contexts.runInContext(context, () -> this.method.invoke(this.beanInstance, MessageUtils.unwrap(message, paramType)));
            subscription.request(1);
        } catch (Exception e) {
            //Notify publisher to stop sending
            subscription.cancel();
            throw new MessagingStreamException(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        throw new MessagingStreamException(t);
    }

    @Override
    public void onComplete() {

    }

}
