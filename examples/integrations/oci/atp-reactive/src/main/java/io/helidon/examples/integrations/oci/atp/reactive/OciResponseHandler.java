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

package io.helidon.examples.integrations.oci.atp.reactive;

import java.util.concurrent.CountDownLatch;

import com.oracle.bmc.responses.AsyncHandler;

final class OciResponseHandler<IN, OUT> implements AsyncHandler<IN, OUT> {
    private OUT item;
    private Throwable failed = null;
    private CountDownLatch latch = new CountDownLatch(1);

    protected OUT waitForCompletion() throws Exception {
        latch.await();
        if (failed != null) {
            if (failed instanceof Exception) {
                throw (Exception) failed;
            }
            throw (Error) failed;
        }
        return item;
    }

    @Override
    public void onSuccess(IN request, OUT response) {
        item = response;
        latch.countDown();
    }

    @Override
    public void onError(IN request, Throwable error) {
        failed = error;
        latch.countDown();
    }
}
