/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Interface with default implementations of deprecated methods on {@link LimitAlgorithm}.
 * <p>
 * This interface invokes only non-deprecated methods declared on LimitAlgorithm to provide default implementations for
 * deprecated methods. The real implementation classes of LimitAlgorithm, by also implementing this interface, can themselves
 * implement only the non-deprecated methods. That simplifies them now and will simplify the task of removing various deprecated
 * items later.
 *
 * @deprecated Remove this interface (and remove from the implementation classes the "implements" clauses that refer to this)
 * in 5.0 when we retire the {@link io.helidon.common.concurrency.limits.LimitAlgorithm} methods declared as obsolete.
 */
@SuppressWarnings("removal")
@Deprecated(since = "4.3.0", forRemoval = true)
abstract class LimitAlgorithmDeprecatedBase implements LimitAlgorithm {

    @Override
    public <T> T invoke(Callable<T> callable) throws Exception {
        try {
            return doInvokeObs(callable).result();
        } catch (IgnoreTaskException e) {
            return e.handle();
        }
    }

    @Override
    public void invoke(Runnable runnable) throws Exception {
        run(runnable);
    }

    @Override
    public Optional<Token> tryAcquire() {
        Outcome outcome = tryAcquireOutcome(true);
        return (outcome instanceof Outcome.Accepted accepted)
                ? Optional.of(accepted.token())
                : Optional.empty();
    }

    @Override
    public Optional<Token> tryAcquire(boolean wait) {
        Outcome outcome = doTryAcquireObs(wait);
        return (outcome instanceof Outcome.Accepted accepted)
                ? Optional.of(accepted.token())
                : Optional.empty();
    }

    /**
     * The implementation classes will need to declare their impls of this as public, but those implementation methods
     * can be removed once we retire this interface.
     *
     * @param wait whether to wait or not
     * @return outcome
     */
    abstract Outcome doTryAcquireObs(boolean wait);

    /**
     * The implementation classes will need to declare their impls of this as public, but those implementation methods
     * can be removed once we retire this interface.
     *
     * @param callable the callable to execute
     * @return result
     */
    abstract <T> Result<T> doInvokeObs(Callable<T> callable) throws Exception;
}
