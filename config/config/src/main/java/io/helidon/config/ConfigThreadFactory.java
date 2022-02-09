/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Config internal {@link ThreadFactory} implementation to customize thread name.
 */
class ConfigThreadFactory implements ThreadFactory {

    private static final AtomicInteger POOL_NUMBER = new AtomicInteger(1);
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;
    private final ClassLoader ccl;
    private final boolean daemon;

    /**
     * Initializes ThreadFactory with specified type of factory to be used in thread name.
     *
     * @param type name of type of thread factory used just to customize thread name
     */
    ConfigThreadFactory(String type) {
        group = Thread.currentThread().getThreadGroup();
        namePrefix = "config-" + POOL_NUMBER.getAndIncrement() + ":" + type + "-";
        ccl = Thread.currentThread().getContextClassLoader();
        daemon = true;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread t = new Thread(group, runnable, namePrefix + threadNumber.getAndIncrement(), 0);
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        t.setContextClassLoader(ccl);
        t.setDaemon(daemon);

        return t;
    }
}
