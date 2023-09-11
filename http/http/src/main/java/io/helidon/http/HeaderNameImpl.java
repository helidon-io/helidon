/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.util.Objects;

record HeaderNameImpl(String lowerCase, String defaultCase) implements HeaderName {

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        /*
        A Http.HeaderName can only be HeaderNameImpl, or HeaderNameEnum.
        If you attempt to create a new name that is already an enum, you should get that enum, so we only have to
        care about HeaderNameImpl here
         */
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (HeaderNameImpl) obj;
        return this.lowerCase.equals(that.lowerCase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowerCase);
    }
}
