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

RUN microdnf install postgresql-server && microdnf clean all
RUN mkdir -p /var/run/postgresql && \
    chown postgres:postgres /var/run/postgresql
ADD entrypoint.sh /usr/local/bin/
ENV PGDATA /var/lib/pgsql/data
USER postgres
ENTRYPOINT ["entrypoint.sh"]
STOPSIGNAL SIGINT
EXPOSE 5432
CMD ["postgres"]
