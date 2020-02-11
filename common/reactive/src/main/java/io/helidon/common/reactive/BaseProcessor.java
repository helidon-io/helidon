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
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A generic processor used for the implementation of {@link Multi} and {@link Single}.
 * <p>
 * Implementing Processor interface, delegating invocations to downstreamSubscribe, next and complete
 * at the right times
 * <p>
 * Such right times are:
 * <ul>
 * <li>1. downstream onSubscription - only after both downstream called subscribe and upstream called
 * onSubscription</li>
 * <li>2. downstream onNext - will not happen before request(...)</li>
 * <li>3. upstream request / cancel - will not happen before request(...) / cancel()</li>
 * <li>4. request(...) / cancel() - will not happend before downstream onSubscription</li>
 * <li>5. downstream onError / onComplete - will not happen before downstream onSubcription</li>
 * <li>6. upstream onError / onComplete - may happen any time after upstream onSubscription, so keep track
 * of upstream completion</li>
 * <li>7. upstream onNext - will not happen before request(...)</li>
 * </ul>
 * <p>
 * The order in which subscribe and onSubscription get called is not defined, so if you want to
 * interact with upstream before downstreamSubscribe is called, you need to take care of the possibility
 * that the downstream may have not called subscribe() yet (ie is not set up). In that case you may be
 * better off implementing Processor yourself, not subclassing from BaseProcessor.
 * <p>
 * downstreamSubscribe needs to be called once and only once, whereas subscribe and onSubscription
 * may be called by different threads - so ensuring atomicity of Subscriber's state transition.
 * These methods are meant to be called only once, and by no more than one thread each, no need to do
 * anything more complicated than synchronized.
 *
 * @param <T> subscribed type (input)
 * @param <U> published type (output)
 */
public abstract class BaseProcessor<T, U> implements Processor<T, U>, Subscription {

    private volatile boolean complete;
    private volatile boolean cancelled;
    private Throwable error;
    private Subscriber<? super U> subscriber;
    private Thread nexting;
    private long request;
    private volatile Subscription subscription;
    private final ReentrantLock subscriptionLock = new ReentrantLock();


    /**
     * This is invoked when this processor is in a state when both upstream and downstream subscriptions have
     * been set up.
     */
    protected void downstreamSubscribe() {
        boolean c = complete;
        subscriber.onSubscribe(this);
        // if was complete, signal; if became complete during onSubscription, then already signalled
        if (c) {
            cancel();
            complete(error);
            error = null;
        }
    }

    /**
     * This is invoked when this processor needs to signal onError or onComplete to downstream, and
     * the downstream Subscriber is in a state that is allowed to receive onError / onComplete.
     * It calls cancel(), too, although cancellation is not required, but we can do that as best
     * effort CPU-saving
     *
     * @param ex Exception to complete exceptionally with
     */
    protected void complete(Throwable ex) {
        if (Objects.nonNull(ex)) {
            subscriber.onError(ex);
        } else {
            this.complete();
        }
    }

    /**
     * Complete by sending onComplete signal to down stream.
     */
    protected void complete() {
        subscriber.onComplete();
    }

    /**
     * This is invoked when this processor receives onNext, and downstream Subscriber is
     * in a state that is allowed to receive onNext.
     *
     * @param item to be sent to down stream
     */
    protected void next(T item) {
        nexting = Thread.currentThread();
        request = 0;

        submit(item, subscriber);

        if (request > 0) {
            long req = request;
            request = 0;
            // in a chain of BaseProcessors this will be just one stack frame
            subscription.request(req);
        }
        nexting = null;
    }

    /**
     * Invoke actual onNext signal to down stream.
     *
     * @param item       to be sent down stream
     * @param subscriber subscriber to sent onNext signal to
     */
    @SuppressWarnings("unchecked")
    protected void submit(T item, Subscriber<? super U> subscriber) {
        subscriber.onNext((U) item);
    }

    @Override
    public void subscribe(Subscriber<? super U> s) {
        try {
            subscriptionLock.lock();

            if (subscriber != null) {
                subscriber.onSubscribe(EmptySubscription.INSTANCE);
                subscriber.onError(new IllegalStateException("This Publisher accepts only one Subscriber"));
            }
            if (s instanceof Subscribable) {
                subscriber = s;
            } else {
                // Protection for foreign subscribers
                subscriber = SequentialSubscriber.create(s);
            }
            if (subscription != null) {
                downstreamSubscribe();
            }

        } finally {
            subscriptionLock.unlock();
        }
    }

    /**
     * All Subscriber method invocations are guaranteed to be in total order by Publisher specification,
     * so no need to do anything special.
     */
    @Override
    public void onSubscribe(Subscription sub) {
        try {
            subscriptionLock.lock();

            if (this.subscription != null) {
                sub.cancel();
                return;
            }

            this.subscription = sub;
            // maybe downstream registered earlier - let them know
            if (subscriber != null) {
                downstreamSubscribe();
            }

        } finally {
            subscriptionLock.unlock();
        }
    }

    @Override
    public void onError(Throwable ex) {
        Objects.requireNonNull(ex);
        if (complete) {
            return;
        }
        // it's ok to set complete without cancelling
        // upstream knows Subscription is as good as cancelled
        complete = true;

        if (subscriber == null) {
            this.error = ex;
            return;
        }

        complete(ex);
    }

    @Override
    public void onComplete() {
        if (complete) {
            return;
        }

        complete = true;
        // when upstream knows Subscription is as good as cancelled
        if (subscriber == null) {
            return;
        }

        complete();
    }

    @Override
    public void onNext(T item) {
        if (!cancelled) {
            if (complete) {
                throw new IllegalStateException("Subscription already cancelled!");
            }
            next(item);
        }
    }

    /**
     * request and cancel are not guaranteed to be thread-safe by the Publisher
     * but this is not interacting with any state of this Processor - thread-safety
     * is sub's concern - eventually the problem of the Publisher that provides
     * the highest level Subscription.
     */
    @Override
    public void request(long n) {
        StreamValidationUtils.checkRequestParam(n, this::onError);
        if (cancelled) {
            return;
        }
        // this is not required to be in sync with any changes to complete,
        // so the call to request and the test of complete do not need to
        // be transactional

        // one case to take care of:
        // if request() is called by the same thread that's doing onNext,
        // then postpone requesting until after next(...) - this will tame the recursion
        // (otherwise it will be 2x the chain of Processors - 1x for the chain of onNext,
        // another 1x for the chain of request())
        if (nexting != null && nexting == Thread.currentThread()) {
            if (Long.MAX_VALUE - request > n) {
                request += n;
            } else {
                request = Long.MAX_VALUE;
            }
            return;
        }

        subscription.request(n);
    }

    @Override
    public void cancel() {
        cancelled = true;
        complete = true;
        subscription.cancel();
    }

    Subscriber<? super U> getSubscriber() {
        return subscriber;
    }

    Subscription getSubscription() {
        return subscription;
    }

    Throwable getError() {
        return error;
    }

    void setError(Throwable error) {
        this.error = error;
    }
}
