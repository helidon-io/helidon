#
# Copyright (c) 2025 Oracle and/or its affiliates.
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

FROM azul/zulu-openjdk:23-jdk-crac-latest AS checkpoint
ENV BASE_URI=http://localhost:8080
RUN apt-get update && apt-get install -y curl siege
WORKDIR /helidon
ADD target target
WORKDIR /helidon/target
ADD createSnapshot.sh createSnapshot.sh
# Start the application, warm it up and checkpoint
RUN chmod +x createSnapshot.sh && bash createSnapshot.sh
# Run from checkpoint
CMD [ "java", "-XX:CRaCEngine=warp", "-XX:CRaCRestoreFrom=/helidon/target/cr" ]