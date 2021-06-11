/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.rsocket.cdi;

import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.webserver.rsocket.RSocketSupport;
import io.helidon.webserver.tyrus.TyrusSupport;

import java.util.concurrent.ExecutorService;

/**
 * Class RSupportMp.
 */
class RSocketSupportMp extends TyrusSupport {

    private final ThreadPoolSupplier threadPoolSupplier;

    RSocketSupportMp(TyrusSupport other) {
        super(other);
        threadPoolSupplier = ThreadPoolSupplier.builder()
                .threadNamePrefix("helidon-rsocket-")
                .build();
    }

    /**
     * Returns executor service for rsocket in MP.
     *
     * @return Executor service or {@code null}.
     */
    @Override
    protected ExecutorService executorService() {
        return threadPoolSupplier.get();
    }
}
