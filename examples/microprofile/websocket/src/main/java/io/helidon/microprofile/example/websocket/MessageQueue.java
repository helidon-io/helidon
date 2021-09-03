/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.example.websocket;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.enterprise.context.ApplicationScoped;

/**
 * Class MessageQueue.
 */
@ApplicationScoped
public class MessageQueue {

    private Queue<String> queue = new ConcurrentLinkedQueue<>();

    /**
     * Push string on stack.
     *
     * @param s String to push.
     */
    public void push(String s) {
        queue.add(s);
    }

    /**
     * Pop string from stack.
     *
     * @return The string or {@code null}.
     */
    public String pop() {
        return queue.poll();
    }

    /**
     * Check if stack is empty.
     *
     * @return Outcome of test.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Peek at stack without changing it.
     *
     * @return String peeked or {@code null}.
     */
    public String peek() {
        return queue.peek();
    }
}
