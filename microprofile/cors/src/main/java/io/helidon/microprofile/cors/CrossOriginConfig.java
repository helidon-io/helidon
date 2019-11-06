/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cors;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.helidon.config.Config;

import static io.helidon.microprofile.cors.CrossOriginHelper.parseHeader;

/**
 * Class CrossOriginConfig.
 */
public class CrossOriginConfig implements CrossOrigin {

    private final String pathPrefix;
    private final String[] value;
    private final String[] allowHeaders;
    private final String[] exposeHeaders;
    private final String[] allowMethods;
    private final boolean allowCredentials;
    private final long maxAge;

    private CrossOriginConfig(Builder builder) {
        this.pathPrefix = builder.pathPrefix;
        this.value = builder.value;
        this.allowHeaders = builder.allowHeaders;
        this.exposeHeaders = builder.exposeHeaders;
        this.allowMethods = builder.allowMethods;
        this.allowCredentials = builder.allowCredentials;
        this.maxAge = builder.maxAge;
    }

    /**
     * Returns path prefix.
     *
     * @return Path prefix.
     */
    public String pathPrefix() {
        return pathPrefix;
    }

    @Override
    public String[] value() {
        return value;
    }

    @Override
    public String[] allowHeaders() {
        return allowHeaders;
    }

    @Override
    public String[] exposeHeaders() {
        return exposeHeaders;
    }

    @Override
    public String[] allowMethods() {
        return allowMethods;
    }

    @Override
    public boolean allowCredentials() {
        return allowCredentials;
    }

    @Override
    public long maxAge() {
        return maxAge;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return CrossOrigin.class;
    }

    /**
     * Builder for {@link CrossOriginConfig}.
     */
    static class Builder implements io.helidon.common.Builder<CrossOriginConfig> {

        private static final String[] ALLOW_ALL = {"*"};

        private String pathPrefix;
        private String[] value = ALLOW_ALL;
        private String[] allowHeaders = ALLOW_ALL;
        private String[] exposeHeaders;
        private String[] allowMethods = ALLOW_ALL;
        private boolean allowCredentials;
        private long maxAge = DEFAULT_AGE;

        public Builder pathPrefix(String pathPrefix) {
            this.pathPrefix = pathPrefix;
            return this;
        }

        public Builder value(String[] value) {
            this.value = value;
            return this;
        }

        public Builder allowHeaders(String[] allowHeaders) {
            this.allowHeaders = allowHeaders;
            return this;
        }

        public Builder exposeHeaders(String[] allowHeaders) {
            this.exposeHeaders = exposeHeaders;
            return this;
        }

        public Builder allowMethods(String[] allowMethods) {
            this.allowMethods = allowMethods;
            return this;
        }

        public Builder allowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
            return this;
        }

        public Builder maxAge(long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        @Override
        public CrossOriginConfig build() {
            return new CrossOriginConfig(this);
        }
    }

    static class CrossOriginConfigMapper implements Function<Config, List<CrossOriginConfig>> {

        @Override
        public List<CrossOriginConfig> apply(Config config) {
            List<CrossOriginConfig> result = new ArrayList<>();
            int i = 0;
            do {
                Config item = config.get(Integer.toString(i++));
                if (!item.exists()) {
                    break;
                }
                Builder builder = new Builder();
                item.get("path-prefix").as(String.class).ifPresent(builder::pathPrefix);
                item.get("allow-origins").as(String.class).ifPresent(
                        s -> builder.value(parseHeader(s).toArray(new String[]{})));
                item.get("allow-methods").as(String.class).ifPresent(
                        s -> builder.allowMethods(parseHeader(s).toArray(new String[]{})));
                item.get("allow-headers").as(String.class).ifPresent(
                        s -> builder.allowHeaders(parseHeader(s).toArray(new String[]{})));
                item.get("expose-headers").as(String.class).ifPresent(
                        s -> builder.exposeHeaders(parseHeader(s).toArray(new String[]{})));
                item.get("allow-credentials").as(Boolean.class).ifPresent(builder::allowCredentials);
                item.get("max-age").as(Long.class).ifPresent(builder::maxAge);
                result.add(builder.build());
            } while (true);
            return result;
        }
    }
}
