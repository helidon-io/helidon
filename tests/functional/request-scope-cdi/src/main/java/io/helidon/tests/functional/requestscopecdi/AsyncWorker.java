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

package io.helidon.tests.functional.requestscopecdi;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.Asynchronous;

@RequestScoped
public class AsyncWorker {

    @Inject
    private SharedBean shared;


    @Asynchronous
    public Future<String> asyncOp() {
        return CompletableFuture.completedFuture(shared.secret());
    }
}
