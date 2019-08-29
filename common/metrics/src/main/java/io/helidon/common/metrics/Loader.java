/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
 *
 */
package io.helidon.common.metrics;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Uses the Java service loader mechanism to find an implementation of the
 * internal bridge.
 */
class Loader {

    static InternalBridge loadInternalBridge() {
        for (Iterator<InternalBridge> it = ServiceLoader.load(InternalBridge.class).iterator(); it.hasNext();) {
            return it.next();
        }
        throw new RuntimeException("Could not find implementation of bridge "
                + InternalBridge.class.getName() + " to load");
    }

    private Loader() {}

}
