/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.LazyValue;
import io.helidon.metrics.api.SystemTagsManager;

import io.micrometer.core.instrument.Tag;

/**
 * Adds system tags to every meter registered in Micrometer.
 *
 * <p>
 *     The list of tags is returned to another class that uses it to create a Micrometer meter filter.
 *     Normally in an application this happens once, but in testing environments the system tags manager might be
 *     instantiated multiple times with potentially different settings. This class is notified in such cases and updates
 *     the list of system tags accordingly so the code referring to the list gets the up-to-date values so the single
 *     Micrometer meter filter instance always uses the current group of system tags.
 * </p>
 */
class SystemTagsMeterFilterManager implements Consumer<SystemTagsManager> {

    private static final LazyValue<SystemTagsMeterFilterManager> MANAGER = LazyValue.create(() -> {
        SystemTagsMeterFilterManager result = new SystemTagsMeterFilterManager(SystemTagsManager.instance());
        SystemTagsManager.onChange(result);
        return result;
    });

    static SystemTagsMeterFilterManager instance() {
        return MANAGER.get();
    }


    private final List<Tag> micrometerSystemTags = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private SystemTagsMeterFilterManager(SystemTagsManager systemTagsManager) {
        apply(systemTagsManager);
    }

    @Override
    public void accept(SystemTagsManager systemTagsManager) {
        apply(systemTagsManager);

    }

    Iterable<Tag> tags() {
        return micrometerSystemTags;
    }

    private SystemTagsMeterFilterManager apply(SystemTagsManager systemTagsManager) {
        lock.lock();

        try {
            micrometerSystemTags.clear();
            systemTagsManager.displayTagPairs()
                    .forEach((name, value) -> micrometerSystemTags.add(Tag.of(name, value)));
            return this;
        } finally {
            lock.unlock();
        }
    }
}
