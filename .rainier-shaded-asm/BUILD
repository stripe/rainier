
load(
  '@io_bazel_rules_scala//scala:scala.bzl',
  'scala_binary',
  'scala_library',
  'scala_test'
)

load(
    "@com_github_johnynek_bazel_jar_jar//:jar_jar.bzl",
    "jar_jar"
)

jar_jar(
    name = "asmShaded",
    input_jar = "@org_ow2_asm_asm//jar",
    rules = "shade_rule",
    visibility = [
      '//visibility:public',
    ]
)

jar_jar(
    name = "asmTreeShaded",
    input_jar = "@org_ow2_asm_asm_tree//jar",
    rules = "shade_rule",
    visibility = [
      '//visibility:public',
    ],
)
