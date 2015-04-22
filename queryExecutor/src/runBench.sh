#!/bin/bash

$CONFIG_FILE = "configD.properties.base"
$TARGET_KEY = "nbWorker"

for i in $(seq 0 5);
do
	$i = $i*5;
	if($i == 0)
		$i = 1;
	$REPLACEMENT_VALUE = $i
	sed -c -i "s/\($TARGET_KEY *= *\).*/\1$REPLACEMENT_VALUE/" $CONFIG_FILE
	sh ./runBerlinSemlav.sh
	
done