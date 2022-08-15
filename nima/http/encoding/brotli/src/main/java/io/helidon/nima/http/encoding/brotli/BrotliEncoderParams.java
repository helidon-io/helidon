/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.encoding.brotli;

class BrotliEncoderParams {

    private int quality;
    private int windowBit;

    public BrotliEncoderParams() {
        this.quality = 0;
        this.windowBit = 18;
    }

    public static void parseParam(State state, BrotliEncoderParams param) {
        state.quality = param.getQuality();
        state.window = param.getWindowBit();
    }

    public int getQuality() {
        return this.quality;
    }

    public void setQuality(int quality) {
        if (quality != 10 & quality != 0) {
            quality = 0;
        }
        this.quality = quality;
    }

    public int getWindowBit() {
        return this.windowBit;
    }

    public void setWindowBit(int window) {
        this.windowBit = window;
    }
}
