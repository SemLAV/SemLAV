
QUERIES=`seq 1 18`
K="80 160 320 500"

for i in $QUERIES; do
  for k in $K; do
    java computeRelevantViewsGUN  /home/montoya/gun2012/code/expfiles/berlinData/FiveMillions/300views/500rewritings_query$i /home/montoya/gun2012/code/expfiles/berlinData/FiveMillions/300views/${k}rewritings_relevant_views_query$i $k
  done
done

