/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Helper methods for stream validation.
 */
public class StreamValidationUtils {


    private StreamValidationUtils() {
    }

    /**
     * Validation of Reactive Streams Specification for JVM rule 3.9.
     * <br>
     * While the {@code Subscription} is not cancelled, {@code Subscription.request(long n)}
     * MUST signal onError with a {@link java.lang.IllegalArgumentException} if the argument is less or equal to 0.
     * The cause message SHOULD explain that non-positive request signals are illegal.
     *
     * @param requestParam number of requested items to be validated.
     * @param onExceeded   called if request param invalid provided with spec compliant exception.
     * @return true if requested parameter is valid
     * @see <a href="https://github.com/reactive-streams/reactive-streams-jvm#3.9">reactive-streams/reactive-streams-jvm#3.9</a>
     */
    public static boolean checkRequestParam(long requestParam, Consumer<? super IllegalArgumentException> onExceeded) {
        if (requestParam <= 0) {
            if (Objects.nonNull(onExceeded)) {
                onExceeded.accept(new IllegalArgumentException(String
                        .format("Non-positive subscription request %d, rule 3.9", requestParam)));
            }
            return false;
        }
        return true;
    }
}
