#
# Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

FROM container-registry.oracle.com/os/oraclelinux:9-slim

RUN echo "[mongodb-org-7.0]" >> .tmp && \
    echo "name=MongoDB Repository" >> .tmp && \
    echo "baseurl=https://repo.mongodb.org/yum/redhat/9/mongodb-org/7.0/$(uname -m)/" >> .tmp && \
    echo "gpgcheck=1" >> .tmp && \
    echo "enabled=1" >> .tmp && \
    echo "gpgkey=https://pgp.mongodb.com/server-7.0.asc" >> .tmp && \
    mv .tmp /etc/yum.repos.d/mongodb-org-7.0.repo

RUN microdnf install mongodb-org && microdnf clean all
ADD entrypoint.sh /usr/local/bin/

ENTRYPOINT ["entrypoint.sh"]
STOPSIGNAL SIGINT
EXPOSE 27017
CMD ["mongod"]
