/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.http;

import java.util.ArrayList;
import java.util.List;

final class StatusHelper {
    private static final List<Status> KNOWN = new ArrayList<>(40);
    private static StatusPair[] statuses;

    private StatusHelper() {
    }

    static Status find(int statusCode) {
        for (StatusPair status : statuses) {
            if (status.code == statusCode) {
                return status.status;
            }
        }

        return null;
    }

    static void add(Status status) {
        KNOWN.add(status);
    }

    static void statusesDone() {
        statuses = new StatusPair[KNOWN.size()];
        for (int i = 0; i < KNOWN.size(); i++) {
            statuses[i] = StatusPair.create(KNOWN.get(i));
        }
        KNOWN.clear();
    }

    private record StatusPair(int code, Status status) {
        public static StatusPair create(Status status) {
            return new StatusPair(status.code(), status);
        }
    }
}
