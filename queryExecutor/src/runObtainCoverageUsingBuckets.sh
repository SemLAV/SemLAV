#!/bin/bash

SemLAVPATH=`pwd | rev | cut -d"/" -f 3- | rev`
SETUPS="510views"
QUERIES=`seq 1 18` 
DATASET=TenMillions
MEMSIZE=20480m
j=3

cp configD.properties.base configE.properties
sed -i".bkp" "s|SemLAVPATH|$SemLAVPATH|" configE.properties
sed -i".bkp" "s|DATASET|$DATASET|" configE.properties
for setup in $SETUPS ;do
    sed -i".bkp" "s/[0-9][0-9]*views/$setup/" configE.properties
    for i in $QUERIES ;do
        for k in `seq 1 $j` ;do
            sed -i".bkp" "s/query[0-9][0-9]*/query$i/" configE.properties
            java -cp ".:../lib2/*" obtainNumberRewritings configE.properties $SemLAVPATH/expfiles/berlinOutput/$DATASET/$setup/exec${k}/outputSemLAVquery${i}_${MEMSIZE}_exec${k}/NOTHING/throughput
        done
    done
done
rm configE.properties.bkp
rm configE.properties
