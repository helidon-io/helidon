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

package io.helidon.pico.maven.plugin;

import java.io.Closeable;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.pico.tools.ToolsException;

/**
 * Delegates a functional invocation to be run within the context of a provided ClassLoader.
 */
class ExecHandler implements Closeable {
    private final boolean ownedContext;
    private final IsolatedThreadGroup threadGroup;
    private final URLClassLoader loader;

    /**
     * Creates an instance using the provided threadGroup and loader.
     *
     * @param threadGroup   the containing thread group to use for any/all spawned threads.
     * @param loader        the loader context to invoke the function in
     */
    ExecHandler(
            boolean ownedContext,
            IsolatedThreadGroup threadGroup,
            URLClassLoader loader) {
        this.ownedContext = ownedContext;
        this.threadGroup = threadGroup;
        this.loader = loader;
    }

    /**
     * Creates an instance using the provided threadGroup and loader. The caller is responsible
     * for the lifecycle and closure of the provided context elements.
     *
     * @param threadGroup   the containing thread group to use for any/all spawned threads
     * @param loader        the loader context to invoke the function
     * @return the exec handler instance
     */
    static ExecHandler create(
            IsolatedThreadGroup threadGroup,
            URLClassLoader loader) {
        return new ExecHandler(false, Objects.requireNonNull(threadGroup), Objects.requireNonNull(loader));
    }

    /**
     * Creates a new dedicated thread for each invocation, running within the context of the provided
     * isolated thread group and loader.  If the request supplier returns null, then that is the signal
     * to the implementation to abort the function call.
     *
     * @param reqSupplier   the request supplier (obviously loaded in the caller's thread context/loader)
     * @param fn            the function to call (obviously loaded in the caller's thread/context loader)
     * @param <Req> the function request type
     * @param <Res> the function response type
     * @return res the result (where the caller might have to use reflection to access)
     */
    <Req, Res> Res apply(
            Supplier<Req> reqSupplier,
            Function<Req, Res> fn) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Res> result = new AtomicReference<>();

        Thread bootstrapThread = new Thread(threadGroup, () -> {
            try {
                Req req = reqSupplier.get();
                if (Objects.isNull(req)) {
                    return;
                }
                result.set(fn.apply(req));
            } catch (Throwable t) {
                throw new ToolsException("error in apply", t);
            } finally {
                latch.countDown();
            }
        });
        threadGroup.preStart(bootstrapThread, loader);
        bootstrapThread.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new ToolsException(e.getMessage(), e);
        }

        threadGroup.throwAnyUncaughtErrors();

        return result.get();
    }

    /**
     * Should be closed to clean up any owned/acquired resources.
     */
    @Override
    public void close() throws IOException {
        if (ownedContext) {
            threadGroup.close();
            loader.close();
        }
    }

}
