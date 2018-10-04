/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.reflect.Method;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.FallbackHandler;

/**
 * Class FallbackAntn.
 */
public class FallbackAntn extends MethodAntn implements Fallback {

    /**
     * Constructor.
     *
     * @param method The method.
     */
    public FallbackAntn(Method method) {
        super(method);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends FallbackHandler<?>> value() {
        LookupResult<Fallback> lookupResult = lookupAnnotation(Fallback.class);
        final String override = getParamOverride("value", lookupResult.getType());
        try {
            return override != null
                   ? (Class<? extends FallbackHandler<?>>) Class.forName(override)
                   : lookupResult.getAnnotation().value();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String fallbackMethod() {
        LookupResult<Fallback> lookupResult = lookupAnnotation(Fallback.class);
        final String override = getParamOverride("fallbackMethod", lookupResult.getType());
        return override != null ? override : lookupResult.getAnnotation().fallbackMethod();
    }
}
