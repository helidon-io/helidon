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
 */
package io.helidon.lra;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


public class ThreadContext {
    private static final java.lang.ThreadLocal<ThreadContext> lraContexts = new java.lang.ThreadLocal<>();
    private Stack<URI> stack;

    private ThreadContext(URI url) {
        stack = new Stack<>();
        stack.push(url);
    }

    public static List<Object> getContexts() {
        ThreadContext threadContext = lraContexts.get();
        if (threadContext == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(threadContext.stack);
    }

}

