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

package io.helidon.webserver;

import io.helidon.common.task.HelidonTaskExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Encapsulates operations with {@link Executors}. Helps to workaround
 * limitations of GraalVM for JDK21 which doesn't support execution of
 * virtual threads and Graal.js code together. New versions of GraalVM
 * don't have this limitation, but for those who stick with JDK21, this
 * is a serious limitations in using Helidon 4.0.x
 * <p>
 * By moving these <em>"factories"</em> into separate class, it is easier
 * to use GraalVM's `@Substitute` mechanism and get Helidon and Graal.js working
 * on GraalVM for JDK21. More info
 * <a href="https://github.com/enso-org/enso/pull/10783#discussion_r1768000821">available in PR-10783</a>.
 */
final class ExecutorsFactory {

    private ExecutorsFactory() {
    }

    /** Used by {@link LoomServer} to allocate its executor service.
     *
     * @return {@link Executors#newVirtualThreadPerTaskExecutor()}
     */
    static ExecutorService newLoomServerVirtualThreadPerTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Used by {@link ServerListener} to allocate its reader executor.
     *
     * @return {@link ThreadPerTaskExecutor#create(java.util.concurrent.ThreadFactory)}
     */
    static HelidonTaskExecutor newServerListenerReaderExecutor() {
        return ThreadPerTaskExecutor.create(virtualThreadFactory());
    }

    /** Used by {@link ServerListener} to allocate its shared executor.
     *
     * @return {@link Executors#newThreadPerTaskExecutor(java.util.concurrent.ThreadFactory)}.
     */
    static ExecutorService newServerListenerSharedExecutor() {
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory());
    }


    private static ThreadFactory virtualThreadFactory() {
        return Thread.ofVirtual().factory();
    }
}
