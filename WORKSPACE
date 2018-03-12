workspace(name = "com_stripe_rainier")

git_repository(
    name = "io_bazel_rules_scala",
    remote = "git://github.com/bazelbuild/rules_scala",
    commit = "e7ab594035dcf05ba1a9ee25b4d73725036f6750"
)

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
scala_repositories()
load("@io_bazel_rules_scala//tut_rule:tut.bzl", "tut_repositories")
tut_repositories()
