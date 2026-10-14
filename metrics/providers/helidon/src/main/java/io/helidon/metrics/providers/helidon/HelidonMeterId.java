/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.List;
import java.util.Objects;
import java.util.SortedMap;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Tag;

final class HelidonMeterId implements Meter.Id {
    private final String name;
    private final List<Tag> tags;

    HelidonMeterId(String name, Iterable<Tag> tags) {
        this(name, HelidonTypes.tagMap(tags));
    }

    // Builder tags are already ordered by key and contain only one value per key.
    HelidonMeterId(String name, SortedMap<String, String> tags) {
        this.name = Objects.requireNonNull(name);
        this.tags = HelidonTypes.tagsFrom(tags);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Iterable<Tag> tags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof HelidonMeterId id) {
            return name.equals(id.name) && tags.equals(id.tags);
        }
        if (!(o instanceof Meter.Id id)) {
            return false;
        }
        return name.equals(id.name()) && tags.equals(HelidonTypes.tagsFrom(HelidonTypes.tagMap(id.tags())));
    }

    @Override
    public int hashCode() {
        return 31 * (31 + name.hashCode()) + tags.hashCode();
    }

    @Override
    public String toString() {
        return "HelidonMeterId[name=" + name + ", tags=" + tags + "]";
    }
}
