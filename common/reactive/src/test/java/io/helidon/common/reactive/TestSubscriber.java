/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A dummy subscriber for testing purposes.
 */
class TestSubscriber<T> implements Flow.Subscriber<T> {

    private final CountDownLatch latch;

    private final long initialRequest;

    private final AtomicReference<Flow.Subscription> upstream;

    private final List<T> items;

    private final List<Throwable> errors;

    private volatile int completions;

    /**
     * Construct a {@code TestSubscriber} with no initial request.
     */
    TestSubscriber() {
        this(0L);
    }

    /**
     * Construct a {@code TestSubscriber} with the given non-negative initial request.
     * @param initialRequest the initial request amount, non-negative
     */
    TestSubscriber(long initialRequest) {
        if (initialRequest < 0L) {
            throw new IllegalArgumentException("initialRequest >= 0L required");
        }
        this.initialRequest = initialRequest;
        this.upstream = new AtomicReference<>();
        this.items = Collections.synchronizedList(new ArrayList<>());
        this.errors = Collections.synchronizedList(new ArrayList<>());
        this.latch = new CountDownLatch(1);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription is null");
        if (upstream.compareAndSet(null, subscription)) {
            if (initialRequest != 0L) {
                subscription.request(initialRequest);
            }
        } else {
            subscription.cancel();
            if (upstream.get() != SubscriptionHelper.CANCELED) {
                errors.add(new IllegalStateException("Subscription already set!"));
            }
        }
    }

    @Override
    public void onNext(T item) {
        Objects.requireNonNull(item, "item is null");
        if (upstream.get() == null) {
            errors.add(new IllegalStateException("onSubscribe not called before onNext!"));
        }
        if (!errors.isEmpty()) {
            errors.add(new IllegalStateException("onNext called after onError!"));
        }
        if (completions != 0) {
            errors.add(new IllegalStateException("onNext called after onComplete!"));
        }
        items.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable is null");
        if (upstream.get() == null) {
            errors.add(new IllegalStateException("onSubscribe not called before onError!"));
        }
        errors.add(throwable);
        latch.countDown();
    }

    @Override
    public void onComplete() {
        if (upstream.get() == null) {
            errors.add(new IllegalStateException("onSubscribe not called before onComplete!"));
        }
        if (!errors.isEmpty()) {
            errors.add(new IllegalStateException("onComplete called when error(s) are present"));
        }
        int c = completions;
        if (c > 1) {
            errors.add(new IllegalStateException("onComplete called again"));
        }
        completions = c + 1;
        latch.countDown();
    }

    /**
     * Returns a mutable, thread-safe list of items received so far.
     * @return a mutable, thread-safe list of items received so far
     */
    public final List<T> getItems() {
        return items;
    }

    /**
     * Returns a mutable, thread-safe list of error(s) encountered so far.
     * @return a mutable, thread-safe list of error(s) encountered so far
     */
    public final List<Throwable> getErrors() {
        return errors;
    }

    /**
     * Returns the last error received by this {@code TestSubscriber} or {@code null}
     * if no error happened.
     * @return the last error received by this {@code TestSubscriber} or {@code null}
     */
    public final Throwable getLastError() {
        int n = errors.size();
        if (n != 0) {
            return errors.get(n - 1);
        }
        return null;
    }

    /**
     * Returns true if this {@code TestSubscriber} received any {@link #onComplete()} calls.
     * @return true if this {@code TestSubscriber} has been completed
     */
    public final boolean isComplete() {
        return completions != 0;
    }

    /**
     * Returns the current upstream {@link Flow.Subscription} instance.
     * @return the current upstream {@link Flow.Subscription} instance.
     */
    public final Flow.Subscription getSubcription() {
        return upstream.get();
    }

    /**
     * Assembles an {@link AssertionError} with the current internal state and
     * captured errors.
     * @param message the extra message to add
     * @return the AssertionError instance
     */
    protected final AssertionError fail(String message) {
        StringBuilder sb = new StringBuilder();
        sb
        .append(message)
        .append(" (")
        .append("items: ")
        .append(items.size())
        .append(", errors: ")
        .append(errors.size())
        .append(", completions: ")
        .append(completions)
                ;

        if (upstream.get() == null) {
            sb.append(", no onSubscribe!");
        } else
        if (upstream.get() == SubscriptionHelper.CANCELED) {
            sb.append(", canceled!");
        }

        sb.append(")");

        AssertionError ae = new AssertionError(sb.toString());

        for (Throwable error : errors) {
            ae.addSuppressed(error);
        }

        return ae;
    }

    /**
     * Request more from the upstream.
     * @param n the request amount, positive
     * @return this
     * @throws IllegalArgumentException if {@code n} is non-positive
     * @throws IllegalStateException if {@link #onSubscribe} was not called on this {@code TestSubscriber} yet.
     */
    public final TestSubscriber<T> request(long n) {
        if (n <= 0L) {
            throw new IllegalArgumentException("n > 0L required");
        }
        Flow.Subscription s = upstream.get();
        if (s == null) {
            throw new IllegalStateException("onSubscribe not called yet!");
        }
        s.request(n);
        return this;
    }

    /**
     * Request one more item from the upstream.
     * @return this
     * @throws IllegalStateException if {@link #onSubscribe} was not called on this {@code TestSubscriber} yet.
     */
    public final TestSubscriber<T> request1() {
        Flow.Subscription s = upstream.get();
        if (s == null) {
            throw new IllegalStateException("onSubscribe not called yet!");
        }
        s.request(1);
        return this;
    }

    /**
     * Request {@link Long#MAX_VALUE} items from the upstream.
     * @return this
     * @throws IllegalStateException if {@link #onSubscribe} was not called on this {@code TestSubscriber} yet.
     */
    public final TestSubscriber<T> requestMax() {
        Flow.Subscription s = upstream.get();
        if (s == null) {
            throw new IllegalStateException("onSubscribe not called yet!");
        }
        s.request(Long.MAX_VALUE);
        return this;
    }

    /**
     * Cancel the upstream.
     * @return this
     */
    public final TestSubscriber<T> cancel() {
        SubscriptionHelper.cancel(upstream);
        return this;
    }

    /**
     * Turn a value into a value + class name string.
     * @param o the object turn into a string
     * @return the string representation
     */
    private String valueAndClass(Object o) {
        if (o == null) {
            return "null";
        }
        return o + " (" + o.getClass().getName() + ")";
    }

    /**
     * Assert that this {@code TestSubscriber} received the exactly the expected items
     * in the expected order.
     * @param expectedItems the vararg array of the expected items
     * @return this
     * @throws AssertionError if the number of items or the items themselves are not equal to the expected items
     */
    @SafeVarargs
    public final TestSubscriber<T> assertValues(T... expectedItems) {
        int n = items.size();
        if (n != expectedItems.length) {
            throw fail("Number of items differ. Expected: " + expectedItems.length + ", Actual: " + n + ".");
        }
        for (int i = 0; i < n; i++) {
            T actualItem = items.get(i);
            T expectedItem = expectedItems[i];
            if (!Objects.equals(expectedItem, actualItem)) {
                throw fail("Item @ index " + i + " differ. Expected: " + valueAndClass(expectedItem) + ", Actual: " + valueAndClass(actualItem) + ".");
            }
        }
        return this;
    }

    /**
     * Assert that this {@code TestSubscriber} has received exactly one {@link #onComplete()} call.
     * @return this
     * @throws AssertionError if there was none, more than one {@code onComplete} call or there are also errors
     */
    public final TestSubscriber<T> assertComplete() {
        int c = completions;
        if (c == 0) {
            throw fail("onComplete not called.");
        }
        if (c > 1) {
            throw fail("onComplete called too many times.");
        }
        if (!errors.isEmpty()) {
            throw fail("onComplete called but there are errors.");
        }
        return this;
    }

    /**
     * Assert that this {@code TestSubscriber} has received exactly one {@link Throwable} (subclass) of the
     * given {@code clazz}.
     * @param clazz the expected (parent) class of the Throwable received
     * @return this
     * @throws AssertionError if no errors were received, different error(s) were received, the same error
     * received multiple times or there were also {@code onComplete} calls as well.
     */
    public final TestSubscriber<T> assertError(Class<? extends Throwable> clazz) {
        if (errors.isEmpty()) {
            throw fail("onError not called");
        }

        int found = 0;
        for (Throwable ex : errors) {
            if (clazz.isInstance(ex)) {
                found++;
            }
        }

        if (found == 0) {
            throw fail("Error not found: " + clazz);
        }
        if (found > 1) {
            throw fail("Multiple onError calls with " + clazz);
        }
        if (completions != 0) {
            throw fail("Error found but there were onComplete calls as well");
        }

        return this;
    }

    /**
     * Assert that the upstream called {@link #onSubscribe}.
     * @return this
     * @throws AssertionError if {@code onSubscribe} was not yet called
     */
    public final TestSubscriber<T> assertOnSubscribe() {
        if (upstream.get() == null) {
            throw fail("onSubscribe not called");
        }
        return this;
    }

    /**
     * Assert that this {@code TestSubscriber} received the given expected items in the expected order and
     * then completed normally.
     * @param expectedItems the varargs of items expected
     * @return this
     */
    @SafeVarargs
    public final TestSubscriber<T> assertResult(T... expectedItems) {
        assertOnSubscribe();
        assertValues(expectedItems);
        assertComplete();
        return this;
    }

    /**
     * Assert that this {@code TestSubscriber} received the given expected items in the expected order
     * and the received an {@link #onError} that is an instance of the given class.
     * @param clazz the expected (parent) class of the Throwable received
     * @param expectedItems the vararg array of the expected items
     * @return this
     */
    @SafeVarargs
    public final TestSubscriber<T> assertFailure(Class<? extends Throwable> clazz, T... expectedItems) {
        assertOnSubscribe();
        assertValues(expectedItems);
        assertError(clazz);
        return this;
    }

    /**
     * Assert that this {@code TestSubscriber} received the given number of items.
     * @param count the expected item count
     * @return this
     * @throws AssertionError if the number of items received differs from the given {@code count}
     */
    public final TestSubscriber<T> assertItemCount(int count) {
        int n = items.size();
        if (n != count) {
            throw fail("Number of items differ. Expected: " + count + ", Actual: " + n + ".");
        }
        return this;
    }

    /**
     * Assert that there were no items or terminal events received by
     * this {@code TestSubscriber}.
     * @return this
     * @throws AssertionError if items or terminal events were received
     */
    public final TestSubscriber<T> assertEmpty() {
        assertOnSubscribe();
        assertItemCount(0);
        assertNotTerminated();
        return this;
    }

    /**
     * Assert that there were no items or terminal events received by
     * this {@code TestSubscriber}.
     * @return this
     * @throws AssertionError if items or terminal events were received
     */
    @SafeVarargs
    public final TestSubscriber<T> assertValuesOnly(T... expectedItems) {
        assertOnSubscribe();
        assertValues(expectedItems);
        assertNotTerminated();
        return this;
    }

    /**
     * Assert that there were no terminal events received by this
     * {@code TestSubscriber}.
     * @return this
     * @throws AssertionError if terminal events were received
     */
    public final TestSubscriber<T> assertNotTerminated() {
        if (!errors.isEmpty()) {
            throw fail("Unexpected errror(s) present.");
        }
        if (completions != 0) {
            throw fail("Unexpected completion(s).");
        }
        return this;
    }

    /**
     * Await the termination of this TestSubscriber in a blocking manner.
     * <p>
     *     If the timeout elapses first, a {@link TimeoutException}
     *     is added to the error list.
     *     If the current thread waiting is interrupted, the
     *     {@link InterruptedException} is added to the error list
     * </p>
     * @param timeout the time to wait
     * @param unit the time unit
     * @return this
     */
    public final TestSubscriber<T> awaitDone(long timeout, TimeUnit unit) {
        try {
            if (!latch.await(timeout, unit)) {
                cancel();
                errors.add(new TimeoutException());
            }
        } catch (InterruptedException ex) {
            cancel();
            errors.add(ex);
        }
        return this;
    }

    /**
     * Await the upstream to produce at least the given number of items within
     * 5 seconds, sleeping for 10 milliseconds at a time.
     * <p>
     *     If the timeout elapses first, a {@link TimeoutException}
     *     is added to the error list.
     *     If the current thread waiting is interrupted, the
     *     {@link InterruptedException} is added to the error list
     * </p>
     * @param count the number of items to wait for
     * @return this
     * @see #awaitCount(int, long, long, TimeUnit)
     */
    public final TestSubscriber<T> awaitCount(int count) {
        return awaitCount(count, 10, 5000, TimeUnit.MILLISECONDS);
    }

    /**
     * Await the upstream to produce at least the given number of items within the
     * specified time window.
     * <p>
     *     If the timeout elapses first, a {@link TimeoutException}
     *     is added to the error list.
     *     If the current thread waiting is interrupted, the
     *     {@link InterruptedException} is added to the error list
     * </p>
     * @param count the number of items to wait for
     * @param sleep the time to sleep between count checks
     * @param timeout the maximum time to wait for the items
     * @param unit the time unit
     * @return this
     */
    public final TestSubscriber<T> awaitCount(int count, long sleep, long timeout, TimeUnit unit) {
        long end = unit.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS) + timeout;

        for (;;) {
            if (items.size() >= count) {
                break;
            }
            long now = unit.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (now > end) {
                cancel();
                errors.add(new TimeoutException());
                break;
            }
            try {
                unit.sleep(sleep);
            } catch (InterruptedException ex) {
                cancel();
                errors.add(ex);
                break;
            }
        }

        return this;
    }
}
