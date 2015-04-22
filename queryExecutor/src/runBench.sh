#!/bin/bash

CONFIG_FILE="configD.properties.base"
TARGET_KEY="nbWorker"

for i in $(seq 0 5);do
        i=$(($i*5))
	if [ $i -eq 0 ]
	then
		i=1
	fi
        #sed -i -e '/$i =/ s/= .*/= $i/' $CONFIG_FILE
        sed -i "s#nbWorker=.*#nbWorker=$i#" $CONFIG_FILE
	sh runBerlinSemLAV.sh
        java processResultsSemLAV /home/semlav/semLAV/expfiles/berlinOutput/TenMillions/510views 510views output /home/montoya/semLAV/code/expfiles/berlinData/TenMillions/answersSize /home/semlav/semLAV/results
        echo $i
done
