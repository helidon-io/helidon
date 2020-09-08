/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.cdi;

import java.util.Collection;
import java.util.concurrent.Callable;

import org.jboss.weld.executor.CommonForkJoinPoolExecutorServices;

/**
 * A {@link CommonForkJoinPoolExecutorServices} whose {@link
 * #wrap(Collection)} method simply returns the supplied {@link
 * Collection} of {@link Callable}s unchanged.
 *
 * <p>This class exists to work around <a
 * href="https://issues.redhat.com/browse/WELD-2494"
 * target="_parent">WELD-2494</a>, which, in turn, was working around
 * <a href="https://bugs.openjdk.java.net/browse/JDK-8184335"
 * target="_parent">JDK-8184335</a>.</p>
 *
 * @see #wrap(Collection)
 */
public final class ExecutorServices extends CommonForkJoinPoolExecutorServices {

    /**
     * Creates a new {@link ExecutorServices}.
     *
     * <p>There is no reason for users to call this constructor.</p>
     */
    public ExecutorServices() {
        super();
    }

    /**
     * Returns the supplied {@code tasks} argument unchanged when invoked.
     *
     * @param tasks a {@link Collection} of {@link Callable}s
     * representing tasks that Weld needs to do; may be {@code null}
     *
     * @return the supplied {@code tasks} argument
     *
     * @see <a href="https://issues.redhat.com/browse/WELD-2494"
     * target="_parent">WELD-2494</a>
     */
    @Override
    public <T> Collection<? extends Callable<T>> wrap(Collection<? extends Callable<T>> tasks) {
        return tasks;
    }

}
