#!/bin/bash

sparqlViewsFolder=/home/montoya/semLAVDemo/viewsDemo4
n3ViewsFolder=/home/montoya/semLAVDemo/viewsInstance
constantsFile=/home/montoya/semLAVDemo/constants
relevantViewsFile=/home/montoya/semLAVDemo/relevantViews
usedViewInstantiationsFile=/home/montoya/semLAVDemo/usedViewInstantiations
MEMSIZE=1024m

QUERIES=`seq 1 4`

for i in $QUERIES ;do
    sed -i".bkp" "s/query[0-9][0-9]*/query$i/" configE.properties
    java -XX:MaxHeapSize=1024m -cp ".:../lib2/*" semLAV/obtainRelevantViews configE.properties $relevantViewsFile
done

java -cp ".:../lib2/*" findViewInstantiations $relevantViewsFile $usedViewInstantiationsFile $constantsFile

java -XX:MaxHeapSize=${MEMSIZE} -cp ".:../lib2/*" semLAV/generateViewsInstantiated $usedViewInstantiationsFile $sparqlViewsFolder $n3ViewsFolder $constantsFile

