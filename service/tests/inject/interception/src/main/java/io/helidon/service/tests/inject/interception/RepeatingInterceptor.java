/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.interception;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.service.inject.api.Injection;
import io.helidon.service.inject.api.Interception;
import io.helidon.service.inject.api.InterceptionContext;
import io.helidon.service.inject.api.InterceptionException;

@Injection.NamedByType(Repeat.class)
@Injection.Singleton
class RepeatingInterceptor implements Interception.Interceptor {
    private static final AtomicReference<Invocation> LAST_CALL = new AtomicReference<>();

    static Invocation lastCall() {
        return LAST_CALL.getAndSet(null);
    }

    @Override
    public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
        LAST_CALL.set(new Invocation(ctx.elementInfo().elementName(), Arrays.copyOf(args, args.length)));
        if (args.length < 3) {
            // safeguard
            return chain.proceed(args);
        }
        System.out.println("Repeat");
        // args:
        // 0: String message
        // 1: Boolean modify
        // 2: Boolean repeat
        // 3: Boolean return
        if ((Boolean) args[2]) {
            try {
                chain.proceed(args);
            } catch (Exception e) {
                System.out.println("exception 1: " + e.getClass().getName() + ": " + e.getMessage());
            }
        }
        try {
            return chain.proceed(args);
        } catch (InterceptionException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("exception 2: " + e.getClass().getName() + ": " + e.getMessage());
            return null;
        }
    }
}
