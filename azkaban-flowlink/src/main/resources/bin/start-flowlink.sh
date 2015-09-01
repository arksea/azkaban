#!/bin/bash

bin/azkaban-flowlink-start.sh $@ 2>&1>logs/flowlinkServerLog_`date +%F+%T`.out &
