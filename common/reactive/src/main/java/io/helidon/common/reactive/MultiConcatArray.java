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
        private volatile long pending = INIT;
        private volatile Thread lastThreadCompleting;
        private boolean redo;

        static final long BAD = Long.MIN_VALUE;
        static final long CANCEL = Long.MIN_VALUE + 1;
        static final long SEE_OTHER = Long.MIN_VALUE + 2;
        static final long INIT = Long.MIN_VALUE + 3;

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
            long p0 = pending;
            if (p0 == CANCEL) {
               subscription.cancel();
               return;
            }

            produced++; // assert: matching request(1) has been done by nextSource()
            this.subscription = subscription;
            // assert: requested == SEE_OTHER
            REQUESTED.setOpaque(this, p0); // assert: p0 is guaranteed to be a value of requested never seen before
                                   //    or is a terminal value (when concurrent good requests do not matter)
            long p = (long) PENDING.getAndSet(this, SEE_OTHER);

            if (p == CANCEL) {
               cancel();
               return;
            }

            if (p == produced) {
               return;
            }

            // assert: p > produced, unless p == BAD - there were request() between nextSource()
            //   and this onSubscribe(); invoke request() on their behalf
            long req = unconsumed(p, produced);
            if (req < 0) {
                updateRequest(req);
            } else if (p != p0) {
                // assert: p != BAD, because req > 0 (because p > produced)
                // assert: p0 != BAD, because pending cannot be updated to p > produced after p0 = BAD
                // assert: requested is at least p0; add the remainder that got added to pending
                updateRequest(p - p0);
            }
            subscription.request(req);
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
               // assert: pending == SEE_OTHER
               PENDING.setOpaque(this, produced);
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
                downstream.onComplete();
                return;
            }

            Flow.Publisher<T> nextPub = sources[index++];

            // assert: r >= produced, unless r == BAD - because produced
            //    gets incremented only in response to a preceding request
            r = unconsumed(r, produced - 1); // assert: same as unconsumed(r+1, produced) for
                // r representing a request count (not a terminal state); one request for the future onSubscribe;
                // for other values of r the value of produced is ignored;

            // assert: this will update pending
            updateRequest(r);
            // assert: requested is guaranteed to change between the subscriptions
            //         so request() concurrent with onSubscribe cannot
            //         miss the update of subscription - they will
            //         always see requested change

            nextPub.subscribe(this);
        }

        protected static long unconsumed(long req, long produced) {
            // assert: all invocations of unconsumed ensure req > produced, or
            //   req represents a final state, where produced does not matter -
            //   MAX_VALUE, BAD, CANCEL

            if (req >= INIT && req < Long.MAX_VALUE) {
               if (produced < 0 && Long.MAX_VALUE + produced < req) {
                  req = Long.MAX_VALUE;
               } else {
                  req -= produced;
               }

               // assert: req > 0
            }

            return req;
        }

        @Override
        public void request(long n) {
            Flow.Subscription sub = updateRequest(n <= 0 ? BAD : n);
            if (sub != null) {
                sub.request(n);
            }
        }

        private boolean updatePending(long n) {
            long req;
            long nextReq;
            do {
                req = pending;
                if (req == CANCEL) {
                    return true;
                }

                if (req == SEE_OTHER) {
                    return false;
                }
                nextReq = n < INIT ? n
                        // assert: n >= 0
                        : Long.MAX_VALUE - n <= req ? Long.MAX_VALUE
                        : req + n;
            } while (!PENDING.compareAndSet(this, req, nextReq));

            return true;
        }

        private Flow.Subscription updateRequest(long n) {
            Flow.Subscription sub;
            long req;
            long nextReq;
            do {
               req = requested;
               while (req < INIT) {
                  if (req != SEE_OTHER || updatePending(n)) {
                     return null;
                  }
                  req = requested;
               }

               sub = subscription;
                nextReq = n < INIT ? n
                        // assert: n >= 0
                        : Long.MAX_VALUE - n <= req ? Long.MAX_VALUE
                        : req + n;

            } while (!REQUESTED.compareAndSet(this, req, nextReq));

            if (nextReq < INIT) {
                // assert: good requests should be delivered once and only once to ensure
                //    no double-accounting happens - so we only
                //    attempt delivering to subscription seen before updating requested, and
                //    mutual exclusion between accesses to subscription.request() from
                // request(), nextSource() and onSubscribe() is enforced.
                // When MAX_VALUE is reached, good requests do not need delivering: concurrent
                // request() may attempt to deliver to an old subscription, as it will not be
                // able to observe new subscriptions (new values of requested), but good requests
                // do not need delivering

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
                return subscription;
            }
            return sub;
        }

        @Override
        public void cancel() {
            Flow.Subscription sub = updateRequest(CANCEL);
            if (sub != null) {
                sub.cancel();
            }
        }
    }
}
