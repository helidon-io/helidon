/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Weight;
import io.helidon.common.concurrency.limits.IgnoreTaskException;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.concurrency.limits.LimitException;
import io.helidon.common.concurrency.limits.spi.LimitProvider;
import io.helidon.common.config.Config;

/**
 * {@link java.util.ServiceLoader} service provider for bulkhead
 * limit implementation.
 */
@Weight(70)
public class BulkheadLimitProvider implements LimitProvider {
    private static final String TYPE = "bulkhead";

    /**
     * Constructor required by the service loader.
     */
    public BulkheadLimitProvider() {
    }

    @Override
    public String configKey() {
        return TYPE;
    }

    @Override
    public Limit create(Config config, String name) {
        return BulkheadLimit.create(BulkheadConfig.builder()
                                              .config(config)
                                              .name(name)
                                              .build());
    }

    private static class BulkheadLimit implements Limit {
        private final Bulkhead bulkhead;

        private BulkheadLimit(Bulkhead bulkhead) {
            this.bulkhead = bulkhead;
        }

        public static Limit create(Bulkhead bulkhead) {
            return new BulkheadLimit(bulkhead);
        }

        @Override
        public void invoke(Runnable runnable) throws Exception {
            invoke(() -> {
                runnable.run();
                return null;
            });
        }

        @Override
        public <T> T invoke(Callable<T> callable) throws Exception {
            AtomicReference<Exception> exception = new AtomicReference<>();

            T result;
            try {
                result = bulkhead.invoke(() -> {
                    try {
                        try {
                            return callable.call();
                        } catch (IgnoreTaskException e) {
                            return e.handle();
                        }
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        exception.set(e);
                        return null;
                    }
                });
            } catch (BulkheadException e) {
                throw new LimitException(e);
            }

            Exception thrownChecked = exception.get();
            if (thrownChecked != null) {
                throw thrownChecked;
            }
            return result;
        }

        @Override
        public String name() {
            return bulkhead.name();
        }

        @Override
        public String type() {
            return TYPE;
        }
    }
}
