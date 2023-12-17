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

package io.helidon.inject.tests.interception;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.InvocationContext;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Named("io.helidon.inject.tests.interception.Return")
@Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 100)
class ReturningInterceptor implements Interception.Interceptor {
    private static final AtomicReference<Invocation> LAST_CALL = new AtomicReference<>();

    static Invocation lastCall() {
        return LAST_CALL.getAndSet(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> V proceed(InvocationContext ctx, Chain<V> chain, Object... args) throws Exception {
        LAST_CALL.set(new Invocation(ctx.elementInfo().elementName(), Arrays.copyOf(args, args.length)));
        if (args.length < 4) {
            // safeguard
            return chain.proceed(args);
        }
        System.out.println("Return");
        // args:
        // 0: String message
        // 1: Boolean modify
        // 2: Boolean repeat
        // 3: Boolean return
        if ((Boolean) args[3]) {
            return (V) "fixed_answer";
        }
        return chain.proceed(args);
    }
}
