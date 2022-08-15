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

class ZopfliCostModel {
    public float[] costCmd;
    public float[] costDist;
    public int distanceHistogramSize;
    public float[] literalCosts;
    public float minCostCmd;
    public int numBytes;

    ZopfliCostModel() {
        this.costCmd = new float[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
        this.costDist = new float[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
        this.literalCosts = new float[Constant.BROTLI_NUM_COMMAND_SYMBOLS];
    }

    public static void cleanupZopfliCostModel(ZopfliCostModel model) {
        model.literalCosts = new float[model.literalCosts.length];
        model.costDist = new float[model.costDist.length];
    }

    public static float zopfliCostModelGetLiteralCosts(ZopfliCostModel model, int from, int to) {
        return model.literalCosts[to] - model.literalCosts[from];
    }

    public static float zopfliCostModelGetMinCostCmd(ZopfliCostModel model) {
        return model.minCostCmd;
    }

    public static float zopfliCostModelGetDistanceCost(ZopfliCostModel model, int distcode) {
        return model.costDist[distcode];
    }

    public static float zopfliCostModelGetCommandCost(ZopfliCostModel model, int cmdcode) {
        return model.costCmd[cmdcode];
    }

    public static void initZopfliCostModel(State state, ZopfliCostModel model, int numBytes) {
        model.numBytes = numBytes;
        model.literalCosts = new float[numBytes + 2];
        model.costDist = new float[state.distance.alphabetSizeLimit];
        model.distanceHistogramSize = state.distance.alphabetSizeLimit;
    }
}
