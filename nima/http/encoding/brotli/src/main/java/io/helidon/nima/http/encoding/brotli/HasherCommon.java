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

class HasherCommon {
    public int[][] extra = new int[4][];

    public boolean isSetup = false;
    public boolean isPrepared = false;
    public int dictNumLookups = 0;
    public int dictNumMatches = 0;

    public int type;
    public int bucketBits;
    public int blockBits;
    public int hashLen;
    public int numLastDistancesToCheck;

    HasherCommon() {

    }
}
