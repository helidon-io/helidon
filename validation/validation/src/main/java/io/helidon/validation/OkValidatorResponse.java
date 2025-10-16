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

package io.helidon.validation;

import io.helidon.common.types.Annotation;

class OkValidatorResponse implements ValidatorResponse {
    @Override
    public boolean valid() {
        return true;
    }

    @Override
    public Annotation annotation() {
        throw new IllegalStateException("Cannot get annotation for a check that did not fail");
    }

    @Override
    public String message() {
        throw new IllegalStateException("Cannot get message for a check that did not fail");
    }

    @Override
    public Object invalidValue() {
        throw new IllegalStateException("Cannot get ínvalid value for a check that did not fail");
    }
}
