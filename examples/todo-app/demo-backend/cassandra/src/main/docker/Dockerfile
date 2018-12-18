#
# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
#

FROM cassandra

ADD startup.sh /

RUN chmod -v u=rx,og-rwx /startup.sh

ENTRYPOINT ["/startup.sh"]

EXPOSE 7000 7001 7199 9042 9160

CMD ["cassandra", "-f"]
