## To use this script it is necessary to change the file $SemLAVPATH/mcdsat/mcdsat/mcdsat
## comment line 35: $MODS --write-models $NNF | sed -n 's/{\([0-9 ]*\)}/\1/p' | python $MCDSATDIR/Main.py G $EXP $2 $3 $VIS.pyo $LOG2 &&
## that generate the rewritings, and include instead the line:
## $MODS $NNF
## that will just generate the theory and allows to obtain the number of rewritings without 
## enumerating them.

QUERIES=`seq 4 4`
SemLAVPATH=`pwd | rev | cut -d"/" -f 3- | rev`
SETUP=300views
DATASET=FiveMillions
MEMSIZE=20480m
j=1
k=80

for i in $QUERIES; do
    java computeCoverageGUN  /home/montoya/data/berlinData/FiveMillions/300views/500rewritings_query$i $SemLAVPATH/expfiles/berlinData/conjunctiveQueries/query${i} /home/montoya/data/berlinData/FiveMillions/300views/mappingsBerlin /tmp/mappingsBerlin $SemLAVPATH/ssdsat/driver.py /tmp/usedViews_query${i} $k 
done

