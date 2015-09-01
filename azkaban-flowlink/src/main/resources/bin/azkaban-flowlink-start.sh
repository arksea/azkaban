#!/bin/bash

azkaban_dir=$(dirname $0)/..
echo $azkaban_dir;
java -Xmx2G -server -jar $azkaban_dir/azkaban-flowlink-1.0.0.jar $@ &
echo $! > $azkaban_dir/currentpid
