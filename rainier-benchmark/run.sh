#!/bin/sh
sbt "project rainierBenchmark" jmh:run | ruby rainier-benchmark/format.rb > rainier-benchmark/benchmarks.txt