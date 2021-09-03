/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.dbclient.common.tests.dbresult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.logging.Logger;

import io.helidon.common.reactive.Multi;
import io.helidon.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT.Type;

import org.junit.jupiter.api.Test;

import static io.helidon.tests.integration.dbclient.common.AbstractIT.DB_CLIENT;
import static io.helidon.tests.integration.dbclient.common.AbstractIT.TYPES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verify proper flow control handling in query processing.
 */
public class FlowControlIT {

    private static final Logger LOGGER = Logger.getLogger(FlowControlIT.class.getName());

    /**
     * Test subscriber.
     * Verifies proper flow control handling of returned pokemon types.
     */
    private static final class TestSubscriber implements Subscriber<DbRow> {

        /** Requests sequence. Total amount of pokemon types is 18. */
        private static final int[] REQUESTS = new int[] {3, 5, 4, 6, 1};

        /** Subscription instance. */
        private Subscription subscription;
        /** Current index of REQUESTS array. */
        private int reqIdx;
        /** Currently requested amount. */
        private int requested;
        /** Currently remaining from last request. */
        private int remaining;
        /** Total amount of records processed. */
        private int total;
        /** Whether processing was finished. */
        private boolean finished;
        /** Error message to terminate test. */
        private String error;

        private TestSubscriber() {
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            total = 0;
            reqIdx = 0;
            finished = false;
            error = null;
            // Initially request 3 DbRows.
            requested = REQUESTS[reqIdx];
            remaining = REQUESTS[reqIdx++];
            LOGGER.info(() -> String.format("Requesting first rows: %d", requested));
            this.subscription.request(requested);
        }

        @Override
        public void onNext(final DbRow dbRow) {
            final Type type = new Type(dbRow.column(1).as(Integer.class), dbRow.column(2).as(String.class));
            total++;
            if (remaining > 0) {
                LOGGER.info(() -> String.format(
                        "NEXT: tot: %d req: %d rem: %d type: %s", total, requested, remaining, type.toString()));
                remaining -= 1;
                if (remaining == 0 && reqIdx < REQUESTS.length) {
                    LOGGER.info(() -> String.format("Notifying main thread to request more rows"));
                    synchronized (this) {
                        this.notify();
                    }
                }
                // Shall not recieve dbRow when not requested!
            } else {
                LOGGER.warning(() -> String.format(
                        "NEXT: tot: %d req: %d rem: %d type: %s", total, requested, remaining, type.toString()));
                throw new IllegalStateException(String.format("Recieved unexpected row: %s", type.toString()));
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable.getMessage();
            LOGGER.warning(() -> String.format("EXCEPTION: %s", throwable.getMessage()));
            finished = true;
        }

        @Override
        public void onComplete() {
            LOGGER.info(() -> String.format("COMPLETE: tot: %d req: %d rem: %d", total, requested, remaining));
            finished = true;
            synchronized (this) {
                this.notify();
            }
        }

        public boolean canRequestNext() {
            return remaining == 0 && reqIdx < REQUESTS.length;
        }

        public void requestNext() {
            if (reqIdx < REQUESTS.length) {
                requested = remaining = REQUESTS[reqIdx++];
                LOGGER.info(() -> String.format("Requesting more rows: %d", requested));
                this.subscription.request(requested);
            } else {
                fail("Can't request more rows, processing shall be finished now.");
            }
        }

    }

    /**
     * Source data verification.
     *
     */
    @Test
    public void testSourceData() {
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types"));
        assertThat(rows, notNullValue());
        List<DbRow> list = rows.collectList().await();
        assertThat(list, not(empty()));
        assertThat(list.size(), equalTo(18));
        for (DbRow row : list) {
            Integer id = row.column(1).as(Integer.class);
            String name = row.column(2).as(String.class);
            final Type type = new Type(id, name);
            assertThat(name, TYPES.get(id).getName().equals(name));
            LOGGER.info(() -> String.format("Type: %s", type.toString()));
        }
    }

    /**
     * Flow control test.
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    @Test
    @SuppressWarnings("SleepWhileInLoop")
    public void testFlowControl() throws InterruptedException {
        CompletableFuture<Long> rowsFuture = new CompletableFuture<>();
        TestSubscriber subscriber = new TestSubscriber();
        Multi<DbRow> rows = DB_CLIENT.execute(exec -> exec
                .namedQuery("select-types"));

        rows.subscribe(subscriber);
        while (!subscriber.finished) {
            synchronized (subscriber) {
                try {
                    subscriber.wait(20000);
                } catch (InterruptedException ex) {
                    fail(String.format("Test failed with exception: %s", ex.getMessage()));
                }
            }
            if (subscriber.canRequestNext()) {
                // Small delay before requesting next records to see whether some unexpected will come
                Thread.sleep(500);
                subscriber.requestNext();
            } else {
                LOGGER.info(() -> String.format("All requests were already done."));
            }
        }
        if (subscriber.error != null) {
            fail(subscriber.error);
        }
    }

}
