/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

/**
 * Can be closed and has information about the state.
 * <p>
 * The goal is to provide just enough synchronisation.
 */
class CloseableSupport implements AutoCloseable {

    private boolean closed = false;
    private volatile boolean closedVolatile = false;

    @Override
    public void close() {
        closed = true;
        closedVolatile = true;
    }

    /**
     * Returns {@code true} if it is closed.
     *
     * @return {@code true} if it is closed
     */
    boolean closed() {
        return closed || closedVolatile;
    }
}
