#!/usr/bin/env bash

# using apache benchmark
# Parameters:
# $1 number of requests to perform
# $2 number of parallel requests (concurrency)
# -k keep alive

ab -n $1 -c $2 -s 2 http://localhost:8099/

