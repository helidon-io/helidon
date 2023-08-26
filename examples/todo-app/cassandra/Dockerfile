#
# Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

FROM cassandra:3.11.16

ADD startup.sh /

RUN chmod -v u=rx,og-rwx /startup.sh

ENTRYPOINT ["/startup.sh"]

EXPOSE 7000 7001 7199 9042 9160

CMD ["cassandra", "-f"]
