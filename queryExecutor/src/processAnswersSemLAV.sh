#!/bin/bash

folder=$1
answer=$2
withPR=$3

file="${folder}/throughput"
out="${folder}/answersInfo"
rvi="${folder}/newRVi"

echo -e "# Precision\tRecall\tTotal number of answers" >> $out
bestSize=-1
best=-1
for solution in `ls $folder/solution*`; do
    #echo $solution
    tmpSize=`ls -l $solution | cut -d" " -f 5`
    #echo $tmpSize
    if [ $tmpSize -gt $bestSize ]; then
        bestSize=$tmpSize
        best=$solution
    fi
done

bestSize=`wc -l $best | cut -d " " -f 1`

if [ "$withPR" = true ]; then
    r=1.00
    p=1.00

    LANG=En_US sort $best > ${best}.sorted
    mv ${best}.sorted ${best}

    # compute recall and precision
    N=`wc -l $answer | sed 's/^[ ^t]*//' | cut -d' ' -f1`
    n=`wc -l $best | sed 's/^[ ^t]*//' | cut -d' ' -f1`

    if [ $N -eq 0 ]; then
        r=1.00
        if [ $n -eq 0 ]; then
            p=1.00
        else
            p=0.00
        fi
    else
        if [ $n -eq 0 ]; then
            p=1.00
            r=0.00
        else
            IntersectionSize=`LANG=En_US comm -12 $best $answer | wc -l | sed 's/^[ ^t]*//' | cut -d' ' -f1`
            p=`echo "scale=2; $IntersectionSize/$n" | bc`
            r=`echo "scale=2; $IntersectionSize/$N" | bc`
        fi
    fi
    echo -e "${p}\t${r}\t${bestSize}" >> $out
else
    echo -e "?\t?\t${bestSize}" >> $out
fi

rm $folder/solution*
