/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.webserver.context.propagation;

import java.util.List;
import java.util.Optional;

import io.helidon.webserver.ServerRequest;

class ArrayRecord implements PropagationRecord {
    private final String classifier;
    private final String headerName;
    private final Optional<String[]> defaultValue;

    ArrayRecord(String classifier, String headerName, String[] defaultValue) {
        this.classifier = classifier;
        this.headerName = headerName;
        this.defaultValue = Optional.ofNullable(defaultValue);
    }

    @Override
    public void apply(ServerRequest req) {
        List<String> all = req.headers().all(headerName);
        if (all.isEmpty() && defaultValue.isPresent()) {
            req.context().register(classifier, defaultValue.get());
            return;
        }
        if (!all.isEmpty()) {
            req.context().register(classifier, all.toArray(new String[0]));
        }
    }

    @Override
    public String toString() {
        return headerName + " -> " + classifier + " (String[])";
    }
}
