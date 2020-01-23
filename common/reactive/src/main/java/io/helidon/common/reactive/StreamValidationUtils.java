/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Helper methods for stream validation.
 */
public class StreamValidationUtils {

    private static final long USE_STACK_WALKER = -1000;
    private static ThreadLocal<Map<String, AtomicLong>> recursionDepthThreadLocal = new ThreadLocal<>();


    private StreamValidationUtils() {
    }

    /**
     * Validation of Reactive Streams Specification for JVM rule 3.3.
     * <br>
     * {@code Subscription.request} MUST place an upper bound on possible synchronous
     * recursion between {@code Publisher} and {@code Subscriber}.
     *
     * @param key          key to differentiate multiple checks on one thread
     * @param maxDepth     maximal expected recursion depth
     * @param guardedBlock code block to check recursion for
     * @param onExceeded   called if recursion is deeper than maxDepth,
     *                     provided with actual depth and spec compliant exception.
     * @param <T>          payload type of the subscriber
     * @return true if valid
     * @see <a href="https://github.com/reactive-streams/reactive-streams-jvm#3.3">reactive-streams/reactive-streams-jvm#3.3</a>
     */
    public static <T> boolean checkRecursionDepth(String key, int maxDepth, Runnable guardedBlock,
                                                  BiConsumer<Long, Throwable> onExceeded) {
        Map<String, AtomicLong> counterMap = recursionDepthThreadLocal.get();
        if (Objects.isNull(counterMap)) {
            counterMap = new HashMap<>();
            counterMap.put(key, new AtomicLong(0));
            recursionDepthThreadLocal.set(counterMap);
        }

        counterMap.putIfAbsent(key, new AtomicLong(0));
        AtomicLong recursionDepthCounter = counterMap.get(key);

        if (recursionDepthCounter.get() <= USE_STACK_WALKER) {
            // We have lost count last time lets reset expensive way
            long recursionDepth = getRecursionDepthByStackAnalysis();
            recursionDepthCounter.set(--recursionDepth);
        }

        if (recursionDepthCounter.incrementAndGet() > maxDepth) {
            long exceededRecursionDepth = recursionDepthCounter.get();
            //Do reset in case exception is thrown
            recursionDepthCounter.set(USE_STACK_WALKER);
            Optional.of(onExceeded)
                    .ifPresent(onExc -> onExc
                            .accept(exceededRecursionDepth, new IllegalCallerException(String
                                    .format("Recursion depth exceeded, max depth expected %d but actual is %d, rule 3.3",
                                            maxDepth, exceededRecursionDepth))));
            return false;
        }
        try {
            guardedBlock.run();
            recursionDepthCounter.decrementAndGet();
            return true;
        } catch (Throwable e) {
            // Do reset in case exception is thrown
            // we cant track exception jump up the stack so lets reset with stack-walker next time
            recursionDepthCounter.set(USE_STACK_WALKER);
            throw e;
        }
    }

    private static Long getRecursionDepthByStackAnalysis() {
        StackTraceElement parentElement = StackWalker.getInstance()
                .walk(stackFrameStream -> stackFrameStream.skip(1).findFirst())
                .get()
                .toStackTraceElement();
        return StackWalker.getInstance()
                .walk(ss -> ss
                        .map(StackWalker.StackFrame::toStackTraceElement)
                        .filter(el -> stackTraceElementEquals(el, parentElement))
                        .count());
    }

    private static boolean stackTraceElementEquals(StackTraceElement a, StackTraceElement b) {
        return Objects.equals(a.getClassLoaderName(), b.getClassLoaderName())
                && Objects.equals(a.getModuleName(), b.getModuleName())
                && Objects.equals(a.getModuleVersion(), b.getModuleVersion())
                && Objects.equals(a.getClassName(), b.getClassName())
                && Objects.equals(a.getMethodName(), b.getMethodName());

    }

    /**
     * Validation of Reactive Streams Specification for JVM rule 3.9.
     * <br>
     * While the {@code Subscription} is not cancelled, {@code Subscription.request(long n)}
     * MUST signal onError with a {@link java.lang.IllegalArgumentException} if the argument is less or equal to 0.
     * The cause message SHOULD explain that non-positive request signals are illegal.
     *
     * @param requestParam number of requested items to be validated.
     * @param onExceeded   called if request param invalid provided with spec compliant exception.
     * @return true if requested parameter is valid
     * @see <a href="https://github.com/reactive-streams/reactive-streams-jvm#3.9">reactive-streams/reactive-streams-jvm#3.9</a>
     */
    public static boolean checkRequestParam(long requestParam, Consumer<? super IllegalArgumentException> onExceeded) {
        if (requestParam <= 0) {
            Optional.of(onExceeded)
                    .ifPresent(onExc -> onExc
                            .accept(new IllegalArgumentException(String
                                    .format("Non-positive subscription request %d, rule 3.9", requestParam))));
            return false;
        }
        return true;
    }
}
