/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package spec

import (
	"errors"
	"fmt"
	"net"
	"testing"
)

func TestRunListenerStopsAfterClose(t *testing.T) {
	for _, test := range []struct {
		name   string
		errors []error
	}{
		{"closed", []error{net.ErrClosed}},
		{"wrapped closed", []error{fmt.Errorf("listener shutdown: %w", net.ErrClosed)}},
		{"other errors still retry", []error{errors.New("temporary accept failure"), net.ErrClosed}},
	} {
		t.Run(test.name, func(t *testing.T) {
			listener := &closeTestListener{errors: test.errors}
			failure := func() (failure interface{}) {
				// The scripted listener stops an unexpected retry, so the unpatched
				// implementation cannot spin forever or flood the build log.
				defer func() { failure = recover() }()
				new(Server).RunListener(listener, nil)
				return nil
			}()
			if failure != nil {
				t.Fatalf("RunListener did not stop after closure: %v", failure)
			}
			if listener.calls != len(test.errors) {
				t.Fatalf("Accept calls = %d, want %d", listener.calls, len(test.errors))
			}
		})
	}
}

type closeTestListener struct {
	errors []error
	calls  int
}

func (listener *closeTestListener) Accept() (net.Conn, error) {
	listener.calls++
	if listener.calls > len(listener.errors) {
		panic("Accept retried after net.ErrClosed")
	}
	return nil, listener.errors[listener.calls-1]
}

func (listener *closeTestListener) Close() error { return nil }

func (listener *closeTestListener) Addr() net.Addr { return &net.TCPAddr{} }
