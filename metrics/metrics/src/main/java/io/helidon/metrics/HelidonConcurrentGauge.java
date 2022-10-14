/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.metrics;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.Metadata;

/**
 * Implementation of {@link ConcurrentGauge}.
 */
final class HelidonConcurrentGauge extends MetricImpl implements ConcurrentGauge {
    private final ConcurrentGauge delegate;

    private HelidonConcurrentGauge(String registryType, Metadata metadata, ConcurrentGauge delegate) {
        super(registryType, metadata);
        this.delegate = delegate;
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata) {
        return create(registryType, metadata, Clock.system());
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata, Clock clock) {
        return create(registryType, metadata, new ConcurrentGaugeImpl(clock));
    }

    static HelidonConcurrentGauge create(String registryType, Metadata metadata, ConcurrentGauge metric) {
        return new HelidonConcurrentGauge(registryType, metadata, metric);
    }

    @Override
    public void inc() {
        delegate.inc();
    }

    @Override
    public void dec() {
        delegate.dec();
    }

    @Override
    public long getCount() {
        return delegate.getCount();
    }

    @Override
    public long getMax() {
        return delegate.getMax();
    }

    @Override
    public long getMin() {
        return delegate.getMin();
    }

    static class ConcurrentGaugeImpl implements ConcurrentGauge {
        private long count;
        private long lastMax;
        private long lastMin;
        private long currentMax;
        private long currentMin;
        private long lastMinute;
        private final Clock clock;

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();


        ConcurrentGaugeImpl(Clock clock) {
            this.clock = clock;
            count = 0L;
            lastMax = Long.MIN_VALUE;
            lastMin = Long.MAX_VALUE;
            currentMax = Long.MIN_VALUE;
            currentMin = Long.MAX_VALUE;
            lastMinute = currentTimeMinute();
        }

        @Override
        public long getCount() {
            return count;
        }

        @Override
        public long getMax() {
            updateState();
            return readAccess(() -> {
                final long max = lastMax;
                return max == Long.MIN_VALUE ? 0L : max;
            });
        }

        @Override
        public long getMin() {
            updateState();
            return readAccess(() -> {
                final long min = lastMin;
                return min == Long.MAX_VALUE ? 0L : min;
            });
        }

        @Override
        public void inc() {
            writeAccess(() -> {
                updateStateLocked();
                count++;
                if (count > currentMax) {
                    currentMax = count;
                }
            });
        }

        @Override
        public void dec() {
            writeAccess(() -> {
                updateStateLocked();
                count--;
                if (count < currentMin) {
                    currentMin = count;
                }
            });
        }

        public void updateState() {
            writeAccess(this::updateStateLocked);
        }

        private Void updateStateLocked() {
            long currentMinute = currentTimeMinute();
            long diff = currentMinute - lastMinute;
            if (diff >= 1L) {
                lastMax = currentMax;
                lastMin = currentMin;
                lastMinute = currentMinute;
            }
            return null;
        }

        private long currentTimeMinute() {
            return clock.milliTime() / 1000 / 60;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), count, lastMin, lastMax);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConcurrentGaugeImpl that = (ConcurrentGaugeImpl) o;
            return count == that.count && lastMin == that.lastMin && lastMax == that.lastMax;
        }

        private void writeAccess(Runnable action) {
            access(lock.writeLock(), action);
        }

        private <T> T readAccess(Callable<T> action) {
            return access(lock.readLock(), action);
        }

        private void access(Lock lock, Runnable action) {
            lock.lock();
            try {
                action.run();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }

        private <T> T access(Lock lock, Callable<T> action) {
            lock.lock();
            try {
                return action.call();
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        HelidonConcurrentGauge that = (HelidonConcurrentGauge) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), delegate);
    }

    @Override
    protected String toStringDetails() {
        StringBuilder sb = new StringBuilder();
        sb.append(", count='").append(getCount()).append('\'');
        sb.append(", min='").append(getMin()).append('\'');
        sb.append(", max='").append(getMax()).append('\'');
        return sb.toString();
    }
}
