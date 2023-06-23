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
package io.helidon.openapi;

/**
 * Behavior for factories able to provide new builders of {@link io.helidon.openapi.OpenApiUi} instances.
 *
 * @param <T> type of the {@link io.helidon.openapi.OpenApiUi} to be built
 * @param <B> type of the builder for T
 */
public interface OpenApiUiFactory<B extends OpenApiUi.Builder<B, T>, T extends OpenApiUi> {

    /**
     * Returns a builder for the UI.
     *
     * @return a builder for the selected type of concrete {@link io.helidon.openapi.OpenApiUi}.
     */
    B builder();
}
