/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Spliterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Tank of events is a closeable FIFO queue with a limited size implementing {@link Valve} reactive API.
 *
 * @param <T> a type of items produced by {@code Valve} API
 */
public class Tank<T> implements Valve<T>, BlockingQueue<T>, AutoCloseable {

    private final int capacity;
    private final CloseableSupport closeableSupport = new CloseableSupport();
    private final Queue<Runnable> drainHandlers = new LinkedBlockingDeque<>();
    private final PausableRegistry<T> registry = new PausableRegistry<T>() {
        @Override
        protected void tryProcess() {
            Tank.this.tryProcess();
        }
    };
    private final ThreadLocal<Boolean> inDrainHandler = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private final ArrayBlockingQueue<T> queue;

    /**
     * Creates new instance.
     *
     * @param capacity the capacity of this queue
     */
    public Tank(int capacity) {
        this.capacity = capacity;
        queue = new ArrayBlockingQueue<>(capacity, true);
    }

    /**
     * Provided handler is called a single time when internal capacity is maximally half full and instance is not closed.
     *
     * @param drainHandler an handler of drain event
     * @throws NullPointerException if {@code drainHandler} is {@code null}
     */
    public void whenDrain(Runnable drainHandler) {
        Objects.requireNonNull(drainHandler, "Parameter 'drainHandler' is null!");
        checkClosed();
        if (!inDrainHandler.get() && remainingCapacity() >= (capacity / 2)) {
            inDrainHandler.set(true);
            try {
                drainHandler.run();
            } finally {
                inDrainHandler.set(false);
            }
        } else {
            drainHandlers.add(drainHandler);
        }
    }

    // ----- Valve implementation

    @Override
    public void pause() {
        registry.pause();
    }

    @Override
    public void resume() {
        registry.resume();
    }

    @Override
    public void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
        registry.handle(onData, onError, onComplete);
    }

    private void tryProcess() {
        if (registry.canProcess()) {
            boolean breakByPause = false;
            try {
                BiConsumer<T, Pausable> onData = registry.getOnData();
                T t;
                while ((t = poll()) != null) {
                    onData.accept(t, this);
                    if (registry.paused()) {
                        breakByPause = true;
                        break;
                    }
                }
            } catch (Exception e) {
                registry.handleError(e);
            } finally {
                if (!breakByPause && closeableSupport.closed()) {
                    // Handle close
                    Runnable onComplete = registry.getOnComplete();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
                registry.releaseProcessing();
            }
            processDrainHandlers();
        }
    }

    private void processDrainHandlers() {
        while (!inDrainHandler.get() && !closeableSupport.closed() && remainingCapacity() >= (capacity / 2)) {
            Runnable hndlr = drainHandlers.poll();
            if (hndlr != null) {
                inDrainHandler.set(true);
                try {
                    hndlr.run();
                } finally {
                    inDrainHandler.set(false);
                }
            } else {
                break;
            }
        }
    }

    // ----- AutoCloseable

    @Override
    public void close() {
        closeableSupport.close();
        tryProcess();
    }

    private void checkClosed() {
        if (closeableSupport.closed()) {
            throw new IllegalStateException("Tank instance is closed!");
        }
    }

    // ----- Insert methods

    @Override
    public boolean add(T t) {
        checkClosed();
        boolean result = queue.add(t);
        tryProcess();
        return result;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        checkClosed();
        boolean result = queue.addAll(c);
        tryProcess();
        return result;
    }

    @Override
    public boolean offer(T t) {
        if (closeableSupport.closed()) {
            return false;
        }
        boolean result = queue.offer(t);
        if (result) {
            tryProcess();
        }
        return result;
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * for space to become available if the queue is full.
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     * @throws IllegalArgumentException if Tank is closed
     */
    @Override
    public void put(T t) throws InterruptedException {
        checkClosed();
        queue.put(t);
        tryProcess();
    }

    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        if (closeableSupport.closed()) {
            return false;
        }
        boolean result = queue.offer(t, timeout, unit);
        if (result) {
            tryProcess();
        }
        return result;
    }

    // ----- Remove methods

    @Override
    public void clear() {
        queue.clear();
    }

    @Override
    public T poll() {
        T t = queue.poll();
        if (t != null) {
            processDrainHandlers();
        }
        return t;
    }

    @Override
    public T take() throws InterruptedException {
        T t = queue.take();
        processDrainHandlers();
        return t;
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        T t = queue.poll(timeout, unit);
        if (t != null) {
            processDrainHandlers();
        }
        return t;
    }

    @Override
    public boolean remove(Object o) {
        boolean result = queue.remove(o);
        if (result) {
            processDrainHandlers();
        }
        return result;
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        int result = queue.drainTo(c);
        if (result > 0) {
            processDrainHandlers();
        }
        return result;
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        int result = queue.drainTo(c, maxElements);
        if (result > 0) {
            processDrainHandlers();
        }
        return result;
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        boolean result = queue.removeIf(filter);
        if (result) {
            processDrainHandlers();
        }
        return result;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean result = queue.removeAll(c);
        if (result) {
            processDrainHandlers();
        }
        return result;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean result = queue.retainAll(c);
        if (result) {
            processDrainHandlers();
        }
        return result;
    }

    @Override
    public T remove() {
        T t = queue.remove();
        if (t != null) {
            processDrainHandlers();
        }
        return t;
    }

    // ----- Query methods (delegated only)

    @Override
    public T element() {
        return queue.element();
    }

    @Override
    public T peek() {
        return queue.peek();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return queue.contains(o);
    }

    @Override
    public Object[] toArray() {
        return queue.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return queue.toArray(a);
    }

    @Override
    public Iterator<T> iterator() {
        return queue.iterator();
    }

    @Override
    public Spliterator<T> spliterator() {
        return queue.spliterator();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        queue.forEach(action);
    }
}
