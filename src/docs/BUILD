load("@io_bazel_rules_scala//tut_rule:tut.bzl", "scala_tut_doc")


scala_tut_doc(
    name = "texts",
    src = "texts.md",
    deps = ["//src/compute", "//src/sampler", "//src/core",  "//src/report"],
)

scala_tut_doc(
    name = "tour",
    src = "tour.md",
    deps = ["//src/compute", "//src/sampler", "//src/core",  "//src/report", "//src/repl"],
)