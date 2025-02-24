/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.testing;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

import io.helidon.common.testing.virtualthreads.PinningRecorder;

import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

import static io.helidon.microprofile.testing.ReflectionHelper.requirePublic;

/**
 * CDI container testing facade.
 */
public class HelidonTestContainer {

    /**
     * Indicate that the container previously failed to initialize.
     */
    public static final class InitializationFailed extends RuntimeException {
        private InitializationFailed(RuntimeException error) {
            super("Container initialization previously failed", error);
        }
    }

    private static final System.Logger LOGGER = System.getLogger(HelidonTestContainer.class.getName());
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final HelidonTestInfo<?> testInfo;
    private final HelidonTestScope testScope;
    private final BiFunction<HelidonTestInfo<?>, HelidonTestScope, HelidonTestExtension> extensionFactory;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ReentrantLock lock = new ReentrantLock();
    private final int id = NEXT_ID.getAndIncrement();
    private SeContainer container;
    private PinningRecorder pinningRecorder;
    private RuntimeException error;

    /**
     * Create a new instance.
     *
     * @param testInfo         test info
     * @param testScope        test scope
     * @param extensionFactory extension factory
     */
    public HelidonTestContainer(HelidonTestInfo<?> testInfo,
                                HelidonTestScope testScope,
                                BiFunction<HelidonTestInfo<?>, HelidonTestScope, HelidonTestExtension> extensionFactory) {

        this.testInfo = testInfo;
        this.testScope = testScope;
        this.extensionFactory = extensionFactory;
    }

    /**
     * Stop the container.
     */
    public void close() {
        if (container != null && closed.compareAndSet(false, true)) {
            LOGGER.log(Level.DEBUG, "closing container id={0}", id);
            container.close();
            if (pinningRecorder != null) {
                pinningRecorder.close();
            }
        }
    }

    /**
     * Indicate if the container is closed.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    public boolean closed() {
        return closed.get();
    }

    /**
     * Indicate if the container initializion failed.
     *
     * @return {@code true} if failed, {@code false} otherwise
     */
    public boolean initFailed() {
        return error != null;
    }

    /**
     * Resolve an unqualified bean of the given type.
     *
     * @param type type
     * @param <T>  type
     * @return resolved instance
     * @throws InitializationFailed if the container previusly failed to
     *                              start
     */
    @SuppressWarnings("resource")
    public <T> T resolveInstance(Class<T> type) throws InitializationFailed {
        if (type.isAssignableFrom(SeContainer.class)) {
            return type.cast(container());
        }
        return container().select(type).get();
    }

    /**
     * Test if the given type is supported for injection.
     *
     * @param type type
     * @return {@code true} if supported, {@code false} otherwise
     * @throws InitializationFailed if the container previusly failed to
     *                              start
     */
    @SuppressWarnings("resource")
    public boolean isSupported(Class<?> type) throws InitializationFailed {
        if (type.isAssignableFrom(SeContainer.class)) {
            return true;
        }
        return !container().select(type).isUnsatisfied();
    }

    private SeContainer container() {
        if (error == null && container == null) {
            try {
                lock.lock();
                if (error == null && container == null) {
                    start();
                }
            } catch (RuntimeException ex) {
                error = ex;
                throw ex;
            } finally {
                lock.unlock();
            }
        }
        if (error != null) {
            throw new InitializationFailed(error);
        }
        return container;
    }

    @SuppressWarnings("unchecked")
    private void start() {
        LOGGER.log(Level.DEBUG, "starting container\n{0}", this);
        if (testInfo.pinningDetection()) {
            pinningRecorder = PinningRecorder.create();
            pinningRecorder.record(Duration.ofMillis(testInfo.pinningThreshold()));
        }
        HelidonTestExtension testExtension = extensionFactory.apply(testInfo, testScope);
        SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        if (testInfo.disableDiscovery()) {
            initializer.disableDiscovery();
        }
        for (AddExtension extension : testInfo.addExtensions()) {
            initializer.addExtensions(requirePublic(extension.value()));
        }
        initializer.addExtensions(testExtension);
        container = initializer.initialize();
    }

    @Override
    public String toString() {
        return new PrettyPrinter()
                .object(printer -> printer
                        .value("id", id)
                        .object("testInfo", PrettyPrinters.testInfo(testInfo)))
                .toString();
    }
}
