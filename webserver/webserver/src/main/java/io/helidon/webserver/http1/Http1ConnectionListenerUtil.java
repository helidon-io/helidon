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

package io.helidon.webserver.http1;

import java.util.LinkedList;
import java.util.List;

final class Http1ConnectionListenerUtil {
    private Http1ConnectionListenerUtil() {
    }

    static Http1ConnectionListener toSingleListener(List<Http1ConnectionListener> sendFrameListeners) {
        if (sendFrameListeners.isEmpty()) {
            return NoOpFrameListener.INSTANCE;
        } else if (sendFrameListeners.size() == 1) {
            return sendFrameListeners.get(0);
        } else {
            return new ListFrameListener(sendFrameListeners);
        }
    }

    // Reverse operation to toSingleListener. Allows builder restoration from existing configuration data.
    static List<Http1ConnectionListener> singleListenerToList(Http1ConnectionListener listener) {
        if (listener instanceof NoOpFrameListener) {
            return new LinkedList<>();
        }
        if (listener instanceof ListFrameListener frameListener) {
            return new LinkedList<>(frameListener.delegates);
        }
        List<Http1ConnectionListener> listeners = new LinkedList<>();
        listeners.add(listener);
        return listeners;
    }

    private static final class NoOpFrameListener implements Http1ConnectionListener {
        private static final NoOpFrameListener INSTANCE = new NoOpFrameListener();
    }

    private static final class ListFrameListener implements Http1ConnectionListener {
        private final List<Http1ConnectionListener> delegates;

        ListFrameListener(List<Http1ConnectionListener> delegates) {
            this.delegates = delegates;
        }

    }

}
