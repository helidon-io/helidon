/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.service.tests.inject.interception;

import io.helidon.service.inject.api.Injection;

@Injection.Singleton
class TheOtherService implements OtherContract {
    private boolean throwException;

    @Modify
    @Construct
    TheOtherService() {
    }

    @Override
    public void throwException(boolean throwException) {
        this.throwException = throwException;
    }

    @Override
    public String intercepted(String message, boolean modify, boolean repeat, boolean doReturn) {
        if (throwException) {
            throwException = false;
            throw new RuntimeException("forced");
        }

        return message;
    }

    // one interceptor on interface, one on implementation
    @Repeat
    @Override
    public String interceptedSubset(String message, boolean modify, boolean repeat, boolean doReturn) {
        if (throwException) {
            throwException = false;
            throw new RuntimeException("forced");
        }

        return message;
    }

    @Override
    public String notIntercepted(String message, boolean modify, boolean repeat, boolean doReturn) {
        if (throwException) {
            throwException = false;
            throw new RuntimeException("forced");
        }

        return message;
    }

}
