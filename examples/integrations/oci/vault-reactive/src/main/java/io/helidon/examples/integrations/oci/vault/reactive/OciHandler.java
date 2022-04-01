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

package io.helidon.examples.integrations.oci.vault.reactive;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.bmc.responses.AsyncHandler;

final class OciHandler {
    private static final Logger LOGGER = Logger.getLogger(OciHandler.class.getName());

    private OciHandler() {
    }

    static <REQ, RES> AsyncHandler<REQ, RES> ociHandler(Consumer<RES> handler) {
        return new AsyncHandler<>() {
            @Override
            public void onSuccess(REQ req, RES res) {
                handler.accept(res);
            }

            @Override
            public void onError(REQ req, Throwable error) {
                LOGGER.log(Level.WARNING, "OCI Exception", error);
                if (error instanceof Error) {
                    throw (Error) error;
                }
                if (error instanceof RuntimeException) {
                    throw (RuntimeException) error;
                }
                throw new RuntimeException(error);
            }
        };
    }
}
