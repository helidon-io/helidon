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

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InternalPublisher implements Publisher<Object> {

    private Method method;
    private Object beanInstance;

    public InternalPublisher(Method method, Object beanInstance) {
        this.method = method;
        this.beanInstance = beanInstance;
    }

    @Override
    public void subscribe(Subscriber<? super Object> s) {
        try {
            s.onNext(method.invoke(beanInstance));
            s.onComplete();
        } catch (IllegalAccessException | InvocationTargetException e) {
            s.onError(e);
        }
    }

}
