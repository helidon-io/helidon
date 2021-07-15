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

package io.helidon.caching.cdi;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.helidon.caching.Cache;
import io.helidon.caching.CacheException;

@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 8)
class InterceptCacheGet {
    private static final Logger LOGGER = Logger.getLogger(InterceptCacheGet.class.getName());

    private final Caches caches;
    private final CachingCdiExtension caching;

    @Inject
    InterceptCacheGet(Caches caches, CachingCdiExtension caching) {
        this.caches = caches;
        this.caching = caching;
    }

    @AroundInvoke
    public Object aroundInvoke(InvocationContext context) throws Exception {
        CachingCdiExtension.MethodInterceptorInfo mi = caching.interceptorInfo(context.getMethod());
        if (mi == null) {
            throw new CacheException("Method interceptor data not ready in CDI extension for method " + context.getMethod());
        }

        Cache<Object, Object> cache = caches.cache(mi.cacheName());
        Object key = mi.cacheKeyFunction().apply(context.getParameters());

        Optional<Object> value = cache.get(key).await();
        if (value.isPresent()) {
            return value.get();
        }
        Object response = context.proceed();
        cache.put(key, response)
                .onError(t -> LOGGER.log(Level.WARNING, "Failed to put response to cache", t));
        return response;
    }
}
