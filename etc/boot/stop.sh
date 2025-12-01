#!/bin/bash

BASEDIR=$1
pushd $BASEDIR > /dev/null
echo -e "Working location at ${BASEDIR} and ready to restart service\n"

PID_FILE='application.pid'

## system environment for boot
export PROJECT_NAME

echo -e "staring to close Application ${PROJECT_NAME} ...\n"

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

popd > /dev/null
