/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import io.helidon.metrics.api.SystemTagsManager;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

/**
 * Adds system tags to every meter registered in Micrometer.
 */
class SystemTagsMeterFilter implements Consumer<SystemTagsManager> {

    private final List<Tag> micrometerSystemTags = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private SystemTagsManager systemTagsManager;

    private SystemTagsMeterFilter(SystemTagsManager systemTagsManager) {
        this.systemTagsManager = systemTagsManager;
        apply(systemTagsManager);
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        if (micrometerSystemTags.isEmpty()) {
            return id;
        }

    }

    @Override
    public void accept(SystemTagsManager systemTagsManager) {
        this.systemTagsManager = systemTagsManager;
        apply(systemTagsManager);

    }

    Iterable<Tag> tags() {
        return micrometerSystemTags;
    }

    private void apply(SystemTagsManager systemTagsManager) {
        lock.lock();

        try {
            micrometerSystemTags.clear();
            systemTagsManager.displayTags()
                    .forEach(tag -> micrometerSystemTags.add(Tag.of(tag.key(),
                                                                    tag.value())));
        } finally {
            lock.unlock();
        }
}
