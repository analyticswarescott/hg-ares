#!/usr/bin/env bash
# Verify these environment variables after installation

export ARES_REPORTING_PREFIX=""
export ARES_HOST=`hostname`

export ARES_HOME=/opt/aw
export ARES_BASE_HOME=/opt/aw
export ARES_ROLES=$ARES_HOME/roles
export ARES_REPORTING=$ARES_ROLES/rest
export ARES_NODE_SERVICE=$ARES_ROLES/node_service
export ARES_NODE_SERVICE_PORT=9100

export CONF_DIRECTORY="$ARES_HOME/conf"
export ARES_LOGS="$ARES_HOME/log"

export JAVA_HOME=$ARES_ROLES/java

export ARES_SPARK_HOME=$ARES_ROLES/spark
export SPARK_LIB_HOME=$ARES_HOME/lib/stream

alias java=$ARES_ROLES/java/bin/java
alias javac=$ARES_ROLES/java/bin/javac
alias node=$ARES_ROLES/ui/bin/node
alias nodejs=$ARES_ROLES/ui/bin/node

# include the deployment environment specific settings
source $ARES_HOME/conf/custom_env.sh



#custom jars
-DCUSTOM_WEBAPP_PKG=com.hg.custom.rest
-DCUSTOM_WEBAPP_JAR=/opt/aw/lib/rest/hg-apps-1.0.0-SNAPSHOT.jar


#
alias stopaw="cd /opt/aw/roles/node_service/bin; ./stop_all.sh; tail -f /opt/aw/log/rest/rest.log"

alias stopnode="cd /opt/aw/roles/node_service/bin; ./node_service.sh stop; jp"
alias startnode="cd /opt/aw/roles/node_service/bin; ./node_service.sh start; touch /opt/aw/log/node_service/node_service.log; tail -f /opt/aw/log/node_service/node_service.log"

#TODO: remove closer to release
alias clean="rm -R /opt/aw/data/*; rm -R /tmp/*"
alias test="cd /opt/aw/tools/bin; chmod +x *.sh; ./test.sh /opt/aw/conf/platform.json ../test/basic_test.json"
alias jp="$JAVA_HOME/bin/jps"

