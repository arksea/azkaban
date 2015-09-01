azkaban_dir=$(dirname $0)/..

#!/bin/bash
proc=`cat $azkaban_dir/currentpid`
echo "killing AzkabanFlowLinkServer"
kill $proc

cat /dev/null > $azkaban_dir/currentpid
