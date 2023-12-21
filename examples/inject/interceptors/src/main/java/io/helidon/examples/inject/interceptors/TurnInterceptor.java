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

package io.helidon.examples.inject.interceptors;

import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.InvocationContext;

@Injection.ClassNamed(Turn.class)
@Injection.Singleton
@SuppressWarnings("unused")
class TurnInterceptor implements Interception.Interceptor {

    @Override
    @SuppressWarnings("unchecked")
    public <V> V proceed(InvocationContext ctx,
                         Chain<V> chain,
                         Object... args) throws Exception {
        // in "real life" you'd use the ctx to determine the best decision - this is just for simple demonstration only!
        if (args.length == 1) {
            // this is the call to turn()
            args[0] = "right";
        } else if (args.length == 0 && ctx.elementInfo().elementName().equals("name")) {
            return (V) ("intercepted: " + chain.proceed(args));
        }

        return chain.proceed(args);
    }

}
