/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webserver.cors;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;

import static io.helidon.webserver.cors.Aggregator.PATHLESS_KEY;
import static io.helidon.webserver.cors.CorsSupportHelper.parseHeader;
import static io.helidon.webserver.cors.CrossOriginConfig.CORS_PATHS_CONFIG_KEY;

/**
 * Loads builders from config. Intended to be invoked from {@code apply} methods defined on the basic and mapped builder classes.
 */
class Loader {

    static class Basic {

        static CrossOriginConfig.Builder applyConfig(CrossOriginConfig.Builder builder, Config config) {
            config.get("enabled")
                    .asBoolean()
                    .ifPresent(builder::enabled);
            config.get("path-expr")
                    .asString()
                    .ifPresent(builder::pathExpr);
            config.get("allow-origins")
                    .asList(String.class)
                    .ifPresent(
                            s -> builder.allowOrigins(parseHeader(s).toArray(new String[]{})));
            config.get("allow-methods")
                    .asList(String.class)
                    .ifPresent(
                            s -> builder.allowMethods(parseHeader(s).toArray(new String[]{})));
            config.get("allow-headers")
                    .asList(String.class)
                    .ifPresent(
                            s -> builder.allowHeaders(parseHeader(s).toArray(new String[]{})));
            config.get("expose-headers")
                    .asList(String.class)
                    .ifPresent(
                            s -> builder.exposeHeaders(parseHeader(s).toArray(new String[]{})));
            config.get("allow-credentials")
                    .as(Boolean.class)
                    .ifPresent(builder::allowCredentials);
            config.get("max-age")
                    .as(Long.class)
                    .ifPresent(builder::maxAgeSeconds);
            return builder;
        }
    }

    static class Mapped {

        static MappedCrossOriginConfig.Builder applyConfig(Config config) {
            return applyConfig(MappedCrossOriginConfig.builder(), config);
        }

        static MappedCrossOriginConfig.Builder applyConfig(MappedCrossOriginConfig.Builder builder, Config config) {
            config.get("enabled").asBoolean().ifPresent(builder::enabled);
            Config pathsNode = config.get(CORS_PATHS_CONFIG_KEY);

            CrossOriginConfig.Builder allPathsBuilder = null;
            int i = 0;
            do {
                Config item = pathsNode.get(Integer.toString(i++));
                if (!item.exists()) {
                    break;
                }
                ConfigValue<CrossOriginConfig.Builder> basicConfigValue = item.as(CrossOriginConfig::builder);
                if (!basicConfigValue.isPresent()) {
                    continue;
                }
                CrossOriginConfig.Builder basicBuilder = basicConfigValue.get();

                /*
                 * We generally maintain the entries in insertion order, but insert any pathless one from config last so the
                 * process of matching request paths against paths in the mapped CORS instance will use any more specific path
                 * expressions before the wildcard.
                 */
                if (basicBuilder.pathExpr().equals(PATHLESS_KEY)) {
                    allPathsBuilder = basicBuilder;
                } else {
                    builder.put(basicBuilder.pathExpr(), basicBuilder);
                }
            } while (true);
            if (allPathsBuilder != null) {
                builder.put(allPathsBuilder.pathExpr(), allPathsBuilder);
            }
            return builder;
        }
    }
}
