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

package io.helidon.common.concurrency.limits;

import java.util.ArrayList;
import java.util.List;

import io.helidon.common.concurrency.limits.spi.LimitAlgorithmListener;

class TestLimitAlgorithmListener implements LimitAlgorithmListener {

    enum Disp {IMM_ACC, IMM_REJ, DEF_ACC, DEF_REJ}
    enum Exec {DROP, IGNORE, SUCCESS}

    record Disposition(Disp disp, String originName, String limitName, long queueingStartTime, long queueingEndTime) {
    }

    private Disposition disposition;
    private Exec exec;

    @Override
    public void onAccept(String originName, String limitName) {
        disposition = new Disposition(Disp.IMM_ACC, originName, limitName, -1, -1);
    }

    @Override
    public void onReject(String originName, String limitName) {
        disposition = new Disposition(Disp.IMM_REJ, originName, limitName, -1, -1);
    }

    @Override
    public void onAccept(String originName, String limitName, long queueingStartTime, long queueingEndTime) {
        disposition = new Disposition(Disp.IMM_ACC, originName, limitName, queueingStartTime, queueingEndTime);
    }

    @Override
    public void onReject(String originName, String limitName, long queueingStartTime, long queueingEndTime) {
        disposition = new Disposition(Disp.DEF_REJ, originName, limitName, queueingStartTime, queueingEndTime);
    }

    @Override
    public void onDrop() {
        exec = Exec.DROP;
    }

    @Override
    public void onIgnore() {
        exec = Exec.IGNORE;
    }

    @Override
    public void onSuccess() {
        exec = Exec.SUCCESS;
    }

    Exec exec() {
        return exec;
    }

    Disposition disposition() {
        return disposition;
    }
}
