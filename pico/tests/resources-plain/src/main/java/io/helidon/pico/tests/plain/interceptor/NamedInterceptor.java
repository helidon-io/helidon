/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.tests.plain.interceptor;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.pico.Interceptor;
import io.helidon.pico.InvocationContext;
import io.helidon.pico.types.DefaultTypeName;
import io.helidon.pico.types.TypedElementName;

@SuppressWarnings({"ALL", "unchecked"})
public class NamedInterceptor implements Interceptor {

    public static final AtomicInteger ctorCount = new AtomicInteger();

    public NamedInterceptor() {
        ctorCount.incrementAndGet();
    }

    @Override
    public <V> V proceed(InvocationContext ctx, Chain<V> chain) {
        assert (Objects.nonNull(ctx));

        TypedElementName methodInfo = ctx.elementInfo();
        if (Objects.nonNull(methodInfo) && methodInfo.typeName().equals(DefaultTypeName.create(long.class))) {
            V result = chain.proceed();
            long longResult = (Long) result;
            Object interceptedResult = (longResult * 2);
            return (V) interceptedResult;
        } else {
            return chain.proceed();
        }
    }

}
