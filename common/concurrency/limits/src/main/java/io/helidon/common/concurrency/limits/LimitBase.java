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

package io.helidon.common.concurrency.limits;

import java.util.concurrent.Callable;

abstract class LimitBase {
    private final String limitExceptionMessage;

    LimitBase(String limitExceptionMessage) {
        this.limitExceptionMessage = limitExceptionMessage;
    }

    <T> LimitAlgorithm.Result<T> doCall(Callable<T> callable) throws Exception {

        LimitAlgorithm.Outcome outcome = doTryAcquireOutcome(true);

        if (!(outcome instanceof LimitAlgorithm.Outcome.Accepted accepted)) {
            throw new LimitException(limitExceptionMessage, outcome);
        }

        LimitAlgorithm.Token token = accepted.token();
        try {
            T response = callable.call();
            token.success();
            return LimitAlgorithm.Result.create(response, outcome);
        } catch (IgnoreTaskException e) {
            token.ignore();
            return LimitAlgorithm.Result.create(e.handle(), outcome);
        } catch (Throwable e) {
            token.dropped();
            throw e;
        }
    }

    abstract LimitAlgorithm.Outcome doTryAcquireOutcome(boolean wait);
}
