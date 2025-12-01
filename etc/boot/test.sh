#!/bin/bash

API_URL=$1
# check web
echo "watting application to start..."
FERQUENCY=1
JARSTS=1;
while ! curl -k --output /dev/null --silent --fail "${API_URL}/actuator"; do
    echo "keep watting application to start..."
    sleep 5
    ((FERQUENCY++))
    if [ "${FERQUENCY}" -eq 36 ]; then
        echo "application start failed..."
		JARSTS=0;
        exit 1
    fi
done

if [ "${JARSTS}" -eq 1 ]; then
    echo "application start success..."
    sleep 1
fi
