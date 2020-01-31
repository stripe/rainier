#!/bin/sh
rm -rf rainier-benchmark/target
sbt "project rainierBenchmark" jmh:run "com.stripe.rainier.bench.sbc.*" | ruby rainier-benchmark/format.rb > rainier-benchmark/benchmarks.txt
