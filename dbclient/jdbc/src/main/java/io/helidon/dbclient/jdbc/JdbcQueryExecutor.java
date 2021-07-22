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
package io.helidon.dbclient.jdbc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An executor that cycles through in-process queries and executes read operations.
 */
class JdbcQueryExecutor {

    private final Random random = new Random();
    private final List<StmtRunnable> runnables = new ArrayList<>();

    void submit(QueryProcessor processor) {
        /*
        The idea is to associate the processor with a thread that is next to have free cycles.
        The same thread should process the same processors - e.g. the tryNext should always read only a single record (or a
        small set of records) from the result set.
        The number of threads to use must be configurable (and may be changing over time such as in an executor service
        Once the query processor completes, we remove it from teh cycle of that thread
        */
        for (StmtRunnable runnable : runnables) {
            if (runnable.processors.isEmpty()) {
                runnable.addProcessor(processor);
                return;
            }
        }
        for (StmtRunnable runnable : runnables) {
            if (runnable.idle.get()) {
                runnable.addProcessor(processor);
                return;
            }
        }
        // none is idle, add it to a random one
        // TODO this must have size limits on the number of processors per runnable - what to do if all busy? add a thread
        // what to do if all threads are used - throw a nice exception
        // we rather refuse work than kill everything
        runnables.get(random.nextInt(runnables.size())).addProcessor(processor);
    }

    interface QueryProcessor {
        boolean tryNext();

        boolean isCompleted();
    }

    // FIXME: This may need some review and redesign.
    private static class StmtRunnable implements Runnable {

        private final Set<QueryProcessor> processors = Collections.newSetFromMap(new IdentityHashMap<>());
        private final AtomicBoolean idle = new AtomicBoolean();
        private final AtomicBoolean enabled = new AtomicBoolean(true);

        void addProcessor(QueryProcessor processor) {
            // lock
            processors.add(processor);
            // unlock
            // we have added a processor, maybe it wants to do stuff immediately
            requestRun();
        }

        void requestRun() {
            // let the next run know, that it should run, or release the waiting "run" method
//            something.release();
        }

        @Override
        public void run() {
            while (enabled.get()) {
                // this is the idle loop
                idle.set(true);
//                something.await();
                idle.set(false);

                // this is the one-cycle of processing loop
                boolean working = true;
                while (working) {
                    working = false;

                    List<QueryProcessor> toRemove = new LinkedList<>();
                    // read lock
                    for (QueryProcessor processor : processors) {
                        if (processor.isCompleted()) {
                            toRemove.add(processor);
                        } else {
                            if (processor.tryNext()) {
                                working = true;
                            }
                        }
                    }
                    // write lock
                    toRemove.forEach(processors::remove);
                }
            }
        }

    }

}
