load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repl")

scala_library(
    name = "deps",
    srcs = glob(["*.scala"]),
    deps = ["//src/compute", "//src/core", "//src/report", "//src/sampler"]
)

scala_repl(
    name = "repl",
    visibility = ["//visibility:public"],
    deps = [":deps"]
)