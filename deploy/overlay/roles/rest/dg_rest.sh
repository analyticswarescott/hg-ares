#!/bin/sh
SERVICE_NAME="ARES REST Service"
CLASSPATH="$ARES_HOME/lib/rest/jetty-runner-9.3.5.v20151012.jar:$ARES_HOME/lib/rest/ares-apps-0.0.1-SNAPSHOT.jar:$ARES_HOME/lib/rest/guava-14.0.jar:$ARES_HOME/lib/rest/jsr311-api-1.1.1.jar:$ARES_HOME/lib/rest/*"
PID_PATH_NAME=/tmp/rest-pid
LOG_FILE=$ARES_HOME/log/rest/rest_startup.log
LOG4J_FILE="$CONF_DIRECTORY/log4j.vm.properties"
export LOG4J_CONFIG_FILE=$LOG4J_FILE
export REST_SYS_PROPS="-DCUSTOM_WEBAPP_PKG=com.hg.custom.rest -DCUSTOM_WEBAPP_JAR=/opt/aw/lib/rest/hg-apps-1.0.0-SNAPSHOT.jar"
case $1 in
    start)
        if [ ! -f $LOG_FILE ]; then
            touch $LOG_FILE
        fi
        echo "Starting $SERVICE_NAME ..."
        if [ ! -f $PID_PATH_NAME ]; then
            nohup $JAVA_HOME/bin/java -Dlog4j.configuration="$LOG4J_FILE" $REST_SYS_PROPS   -cp $CLASSPATH com.aw.ops.RestRoleServer $2 $3 1>> $LOG_FILE 2>&1 &
                        echo $! > $PID_PATH_NAME
            sleep 2s
            PID=$(cat $PID_PATH_NAME);
            PIDS=`ps cax | grep $PID | grep -o '^[ ]*[0-9]*'`
            if [ -z "$PIDS" ]; then
              echo "Process not running; $SERVICE_NAME failed to start. See logfile at $LOG_FILE." 1>&2
              rm $PID_PATH_NAME
              exit 1
            else
                echo "$SERVICE_NAME started, see log at $LOG_FILE ..."
            fi
        else
            PID=$(cat $PID_PATH_NAME);
            PIDS=`ps cax | grep $PID | grep -o '^[ ]*[0-9]*'`
            if [ -z "$PIDS" ]; then
                rm $PID_PATH_NAME
                nohup $JAVA_HOME/bin/java -Dlog4j.configuration="$LOG4J_FILE" $REST_SYS_PROPS -cp $CLASSPATH com.aw.ops.RestRoleServer $2 $3 1>> $LOG_FILE 2>&1 &
                            echo $! > $PID_PATH_NAME
                sleep 2s
                PID=$(cat $PID_PATH_NAME);
                PIDS=`ps cax | grep $PID | grep -o '^[ ]*[0-9]*'`
                if [ -z "$PIDS" ]; then
                  echo "Process not running; $SERVICE_NAME failed to start. See logfile at $LOG_FILE." 1>&2
                  rm $PID_PATH_NAME
                  exit 1
                else
                    echo "$SERVICE_NAME started, see log at $LOG_FILE ..."
                fi
            else
                echo "$SERVICE_NAME is already running ..."
            fi
        fi
    ;;
    stop)
        if [ -f $PID_PATH_NAME ]; then
            PID=$(cat $PID_PATH_NAME);
            echo "$SERVICE_NAME stoping ..."
            kill $PID;
            echo "$SERVICE_NAME stopped ..."
            rm $PID_PATH_NAME
        else
            echo "$SERVICE_NAME is not running ..."
        fi
    ;;
esac
