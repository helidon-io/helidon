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

FROM container-registry.oracle.com/java/openjdk:27-oraclelinux9

# Pin the JDK 28 EA build used by CI.
# SHA-256 values are published alongside the official archives.
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64) platform=linux-x64; checksum=b32bf773d02fff73d4a2da78cf590459879963b3dbffd18dcc9d6c6756ee4549 ;; \
      aarch64) platform=linux-aarch64; checksum=3908eb08a5a6a074301fffbed99ef8f2a36d0ae42dce41e7c948a3b16a8cfa5a ;; \
      *) echo "Unsupported JDK architecture" >&2; exit 1 ;; \
    esac; \
    curl --fail --location --retry 3 --retry-all-errors \
      "https://download.java.net/java/early_access/jdk28/21/GPL/openjdk-28-ea+21_${platform}_bin.tar.gz" \
      --output /tmp/openjdk-28.tar.gz; \
    echo "${checksum}  /tmp/openjdk-28.tar.gz" | sha256sum --check -; \
    mkdir -p /opt/java/jdk-28; \
    tar -xzf /tmp/openjdk-28.tar.gz --strip-components=1 -C /opt/java/jdk-28; \
    rm /tmp/openjdk-28.tar.gz
ENV JAVA_HOME=/opt/java/jdk-28
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /opt/helidon/app
RUN mkdir -p /opt/helidon/app
COPY --from=build /workspace/h2spec/h2specd /usr/local/bin/h2specd

CMD ["/usr/local/bin/h2specd"]
