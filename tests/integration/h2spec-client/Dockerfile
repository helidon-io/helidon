#
# Copyright (c) 2026 Oracle and/or its affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM container-registry.oracle.com/os/oraclelinux:9-slim AS build
ENV GO111MODULE=on
ENV GOPROXY=https://proxy.golang.org
ENV CGO_ENABLED=0
ENV VERSION=2.6.1-SNAPSHOT
ENV COMMIT=af83a65f0b6273ef38bf778d400d98892e7653d8

RUN microdnf install go-toolset git -y

WORKDIR /workspace
RUN git clone https://github.com/summerwind/h2spec.git

WORKDIR /workspace/h2spec
RUN git checkout ${COMMIT}
#
# h2specd hardcodes a 3-second launcher timeout in spec/specd.go. That is too short for a
# JVM client started repeatedly inside a container, so honor the existing --timeout flag instead.
RUN sed -i 's/time.Duration(3) \* time.Second/c.Timeout/' spec/specd.go
RUN grep -nF 'time.After(c.Timeout)' spec/specd.go
RUN go build -ldflags "-X main.VERSION=${VERSION} -X main.COMMIT=${COMMIT}" ./cmd/h2specd

FROM container-registry.oracle.com/java/jdk:26-oraclelinux9

WORKDIR /opt/helidon/app
RUN mkdir -p /opt/helidon/app
COPY --from=build /workspace/h2spec/h2specd /usr/local/bin/h2specd

CMD ["/usr/local/bin/h2specd"]
