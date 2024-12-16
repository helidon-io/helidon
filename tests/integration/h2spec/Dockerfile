#
# Copyright (c) 2024 Oracle and/or its affiliates.
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
RUN go build -ldflags "-X main.VERSION=${VERSION} -X main.COMMIT=${COMMIT}" ./cmd/h2spec

FROM container-registry.oracle.com/os/oraclelinux:9-slim
ARG PORT=8080
ARG HOST=localhost
ENV PORT=${PORT}
ENV HOST=${HOST}
COPY --from=build /workspace/h2spec/h2spec /usr/local/bin/h2spec
CMD ["/usr/local/bin/h2spec", "-h", "${HOST}", "-p", "${PORT}"]