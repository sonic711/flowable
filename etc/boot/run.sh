#!/bin/bash

BASEDIR=$1
pushd $BASEDIR > /dev/null
echo -e "Working location at ${BASEDIR} and ready to restart service\n"
echo -e "${PROJECT_NAME}"
#[ ! -f .env ] || export $(grep -v '^#' .env | xargs)
set -a
[ ! -f ${BASEDIR}/.env ] || . ${BASEDIR}/.env
set +a

PID_FILE='application.pid'

## java environment
export JAVA_HOME
export JAVA_TOOL_OPTIONS
export _JAVA_OPTIONS

## system environment for boot
export PROJECT_NAME

# export LOADER_PATH=${BASEDIR}/lib
# export LOG_ROOT=${BASEDIR}/logs
# export LOG4J2_DISABLE_ANSI=true

# ## log4j2 config file location
# export LOGGING_CONFIG=${BASEDIR}/config/log4j2.yml

## startup args
export JAVA_OPTS="-server -Dnashorn.args=--no-deprecation-warning -Duser.timezone=Asia/Taipei -Dfile.encoding=utf-8 $JAVA_OPTS"

echo "Java Options : ${JAVA_OPTS}"

# export LOG4J_ARGS="-Dlog4j.configurationFile=${LOGGING_CONFIG} -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
# export BOOT_ARGS="--spring.config.location=${SPRING_CONFIG_ADDITIONAL_LOCATION}"
#export BOOT_ARGS="--spring.profiles.active=domain-h2,config, $BOOT_ARGS"

echo -e "staring to boot Application ${PROJECT_NAME} ...\n"

if [ -f ${PID_FILE} ] && kill -15 $(cat ${PID_FILE}) 2>/dev/null; then
      echo "Application is running. Waiting for the completion of shutdown ...."
      sleep 1;
      while [ -f ${PID_FILE} ];
      do
          echo "....."
          sleep 1;
      done;
      echo "done!"
else
      echo "Application is not running. go ahead"
fi

#rm -rf logs/*

JAVA_ARGS="$JAVA_OPTS $LOG4J_ARGS"
command="$JAVA_ARGS -jar $BOOTJAR_LOCATION $BOOT_ARGS"

echo "startup command: $JAVA_HOME/bin/java $command"

nohup $JAVA_HOME/bin/java $command  >/dev/null 2>&1 &

popd > /dev/null
