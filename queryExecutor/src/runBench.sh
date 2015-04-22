#!/bin/bash

CONFIG_FILE="configD.properties.base"
TARGET_KEY="nbWorker"

for i in $(seq 0 5);do
        $i = $i*5
        REPLACEMENT_VALUE = $i
        sed -i "s/\($TARGET_KEY *= *\).*/\1$REPLACEMENT_VALUE/" $CONFIG_FILE
        sh runBerlinSemlav.sh
        java processResultsSemLAV /home/semlav/semLAV/expfiles/berlinOutput/TenMillions/510views 510views output /home/montoya/semLAV/code/expfiles/berlinData/TenMil$
        echo $i
done
