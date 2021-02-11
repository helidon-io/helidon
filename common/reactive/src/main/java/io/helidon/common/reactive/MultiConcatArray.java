/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Flow;

/**
 * Relay items in order from subsequent Flow.Publishers as a single Multi source.
 */
final class MultiConcatArray<T> implements Multi<T> {

    private final Flow.Publisher<T>[] sources;

    MultiConcatArray(Flow.Publisher<T>[] sources) {
        this.sources = sources;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        ConcatArraySubscriber<T> parent = new ConcatArraySubscriber<>(subscriber, sources);
        subscriber.onSubscribe(parent);
        parent.nextSource(parent.produced);
    }

    protected static final class ConcatArraySubscriber<T>
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final Flow.Publisher<T>[] sources;

        private Flow.Subscription subscription;

        private int index;

        private long produced = INIT;

        private volatile long requested = SEE_OTHER;
        private volatile long pending = 0;
        private volatile long oldRequested = 0;
        private volatile Thread lastThreadCompleting;
        private boolean redo;

        static final long BAD       = Long.MIN_VALUE;
        static final long CANCEL    = Long.MIN_VALUE + 1;
        static final long SEE_OTHER = Long.MIN_VALUE + 2;
        static final long INIT      = Long.MIN_VALUE + 3;

        static final VarHandle REQUESTED;
        static final VarHandle PENDING;
        static final VarHandle LASTTHREADCOMPLETING;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                REQUESTED = lookup.findVarHandle(ConcatArraySubscriber.class, "requested", long.class);
                PENDING = lookup.findVarHandle(ConcatArraySubscriber.class, "pending", long.class);
                LASTTHREADCOMPLETING = lookup
                        .findVarHandle(ConcatArraySubscriber.class, "lastThreadCompleting", Thread.class);
            } catch (Exception e) {
                throw new Error("Expected lookup to succeed", e);
            }
        }

        ConcatArraySubscriber(Flow.Subscriber<? super T> downstream, Flow.Publisher<T>[] sources) {
            this.downstream = downstream;
            this.sources = sources;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            produced++; // assert: matching request(1) has been done by nextSource()
            this.subscription = subscription;
            long oldProduced = produced;
            long oldR = oldRequested;

            long p0 = pending;
            if (p0 < 0 && oldR != CANCEL) {
                // not entirely necessary, since BAD and CANCEL must be observed only eventually, but
                // the least surprising behaviour is:
                // if pending is known to be BAD or CANCEL, make sure requested does not
                // appear good even temporarily
                oldR = p0;
            }

            // assert: requested == SEE_OTHER
            requested = oldR; // assume non-conforming upstream Publisher may start delivering onNext or
            // onComplete concurrently upon observing a concurrent request: only use
            // values read before this assignment, or
            // method-locals, or atomic updates competing with request() or cancel()

            if (oldR == CANCEL) {
                subscription.cancel();
                return;
            }

            if (oldR != oldProduced) {
                long req = unconsumed(oldR, oldProduced);
                // assert: req != CANCEL
                subscription.request(req); // assert: requesting necessarily from this subscription;
                //    if a concurrent onComplete is fired by a non-conforming
                //    Publisher before this request, the request is no-op, and onComplete
                //    will carry over req to the next Publisher - no double accounting
                //    occurs;
                //    but if there is no concurrent onComplete, need to request
                //    from this subscription
                //    (plus trivial arithmetical proof based on commutativity of
                //    addition - produced may change concurrently, too, but only by
                //    no more than concurrent requests, and the req can be seen to be
                //    preserved)
            }

            long p = claimPending();
            if (p != 0) { // all concurrent onSubscribe and requests that observe requested >= INIT attempt this
                updateRequest(p);
            }
        }

        @Override
        public void onNext(T item) {
            produced++;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            REQUESTED.setOpaque(this, CANCEL);
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            Thread current = Thread.currentThread();
            if (LASTTHREADCOMPLETING.getOpaque(this) == current) {
                redo = true;
                return;
            }

            LASTTHREADCOMPLETING.setOpaque(this, current);
            VarHandle.storeStoreFence();
            boolean sameThread;
            boolean again;
            do {
                redo = false;
                long r = (long) REQUESTED.getAndSet(this, SEE_OTHER);
                subscription = null;

                nextSource(r);
                again = redo;
                VarHandle.loadLoadFence();
                sameThread = LASTTHREADCOMPLETING.getOpaque(this) == current;
            } while (again && sameThread);

            if (sameThread) {
                LASTTHREADCOMPLETING.compareAndSet(this, current, null);
            }
        }

        protected void nextSource(long r) {
            // assert: requested == SEE_OTHER
            if (r == CANCEL) {
                return;
            }

            if (index == sources.length) {
                // assert: no onSubscribe in the future, so no need to preserve oldRequested
                downstream.onComplete();
                return;
            }

            Flow.Publisher<T> nextPub = sources[index++];

            oldRequested = r < INIT || r == Long.MAX_VALUE ? r : r + 1;

            nextPub.subscribe(this);
        }

        protected static long unconsumed(long req, long produced) {
            // assert: all invocations of unconsumed ensure req > produced, or
            //   req represents a final state, where produced does not matter -
            //   MAX_VALUE, BAD, CANCEL

            if (req >= INIT && req < Long.MAX_VALUE) {
                if (produced < 0 && Long.MAX_VALUE + produced < req) {
                    // assert: if produced < 0, then MAX_VALUE + produced does not overflow
                    req = Long.MAX_VALUE;
                } else {
                    // assert: if produced >= 0, then req-produced does not overflow (req > produced)
                    req -= produced;
                }

                // assert: req > 0
            }

            return req;
        }

        @Override
        public void request(long n) {
            updateRequest(n <= 0 ? BAD : n);
        }

        /*
         * If requested is in a state where update is possible, and there is anything accumulated in
         * the pending counter, try to claim it. If the caller observes a non-zero return value, they
         * must updateRequest with that value.
         */
        private long claimPending() {
            long p;
            do {
                p = pending;
                if (p == 0) {
                    return 0;

                }

                long r = requested;
                if (r < INIT && !(r == BAD && p == CANCEL)) {
                    // assert: updating requested is needed:
                    //
                    // BAD       |  if p == CANCEL
                    // CANCEL    |  no
                    // SEE_OTHER |  no
                    // >= INIT   |  if p != 0
                    return 0;
                }
            } while (!PENDING.compareAndSet(this, p, p < 0 ? p : 0));

            return p;
        }

        private long updatePending(long n) {
            long req;
            long nextReq;
            do {
                req = pending;
                if (req < 0 && !(req == BAD && n == CANCEL)) {
                    // assert: updating pending is needed:
                    //
                    // BAD       |  if n == CANCEL
                    // CANCEL    |  no
                    // >= 0      |  yes
                    break;
                }

                nextReq = n < 0 ? n
                        // assert: n >= 0
                        : Long.MAX_VALUE - n <= req ? Long.MAX_VALUE : req + n;
            } while (!PENDING.compareAndSet(this, req, nextReq));

            return claimPending();
        }

        private void updateRequest(long n) {
            Flow.Subscription sub;
            long req;
            long nextReq;
            do {
                req = requested;
                while (req < INIT && !(req == BAD && n == CANCEL)) {
                    // assert: updating requested is needed:
                    //
                    // BAD       |  if n == CANCEL
                    // CANCEL    |  no - terminal state
                    // SEE_OTHER |  no - keep track of n in pending
                    // >= INIT   |  yes

                    if (req != SEE_OTHER) {
                        return;
                    }
                    n = updatePending(n);
                    if (n == 0) { // assert: requested is in a terminal state, or there is a claimPending()
                        //    now or in the future that will propagate pending to requested and
                        //    the actual Publisher
                        return;
                    }

                    req = requested;
                }

                sub = subscription;
                nextReq = n < INIT ? n
                        // assert: n >= 0
                        : Long.MAX_VALUE - n <= req ? Long.MAX_VALUE : req + n;
            } while (!REQUESTED.compareAndSet(this, req, nextReq));

            if (nextReq < INIT) {
                // assert: good requests should be delivered once and only once to ensure
                //    no double-accounting happens - so we only
                //    attempt delivering to subscription seen before updating requested, and
                //    mutual exclusion between accesses to subscription.request() from
                // request() and onSubscribe() is enforced.
                // When MAX_VALUE is reached, good requests do not need delivering: concurrent
                // request() may miss an update to subscription, and attempt to deliver to an
                // old subscription, as it will not be
                // able to observe new subscriptions (new values of requested), but good requests
                // do not need delivering in this case

                // assert: cancellations and bad requests can be delivered more than once - no
                //    double accounting
                //    occurs, and only one onError will be delivered by upstream Publisher. For
                // this reason can read subscription as it appears after updating requested -
                // this may result in both onSubscribe() and concurrent request() to call
                // subscription.request, but this is ok for a bad request
                // What we do not want to happen, is for bad request to be delivered to an old
                // subscription, the update of which concurrent request() cannot detect after
                // requested reaches MAX_VALUE - so, should read subscription after updating
                // requested
                sub = subscription;

                // assert: subscription may be null, if requested was updated before it was set
                //    to SEE_OTHER by onComplete, but before subscription is set again by
                //    onSubscribe; consequently, if it is null, then there is onSubscribe in the
                //    future that will observe the update of requested and signal appropriately
                if (sub != null) {
                    if (nextReq == CANCEL) {
                        sub.cancel();
                    } else {
                        sub.request(BAD);
                    }
                }
                return;
            }

            // assert: nextReq == req, if req == MAX_VALUE - nothing needs doing
            if (nextReq != req) {
                // assert: sub is not null, because when req != MAX_VALUE the change of subscription
                //    cannot be missed
                sub.request(nextReq - req);
            }
        }

        @Override
        public void cancel() {
            updateRequest(CANCEL);
        }
    }
}
