#!/bin/bash

CONFIG_FILE="configD.properties.base"

for k in $(seq 0 3);do
    k=$(($k*50))
    sed -i "s#nbTripleByLock=.*#nbTripleByLock=$k#" $CONFIG_FILE

    for j in $(seq 0 1);do
		$lock
    	if [ $j -eq 0 ]
    	then
    		lock="SRMW"
    	else
    		lock="MRSW"
    	fi
    	sed -i "s#lockType=.*#lockType=$lock#" $CONFIG_FILE

    	echo "nbTripleByLock : $k" >> "../../results/data510viewsSemLAV.dat"
    	echo "lock : $lock" >> "../../results/data510viewsSemLAV.dat"

    	for i in $(seq 0 6);do
        	i=$(($i*5))
			if [ $i -eq 0 ]
			then
				i=1
			fi
        		sed -i "s#nbWorker=.*#nbWorker=$i#" $CONFIG_FILE
			sh runBerlinSemLAV.sh
        	java processResultsSemLAV /home/semlav/semLAV/expfiles/berlinOutput/TenMillions/510views 510views output /home/montoya/semLAV/code/expfiles/berlinData/TenMillions/answersSize /home/semlav/semLAV/results
        	echo $i
		done
	done
done
