scala_binary(
    name = "fitnormal",
    srcs = ["FitNormal.scala"],
    visibility = ["//visibility:public"],
    main_class = "rainier.models.FitNormal",
    deps = ["//src/compute", "//src/sampler", "//src/core",  "//src/report"]
)

scala_binary(
    name = "fitmixture",
    srcs = ["FitMixture.scala"],
    visibility = ["//visibility:public"],
    main_class = "rainier.models.FitMixture",
    deps = ["//src/compute", "//src/sampler", "//src/core",  "//src/report"]
)

scala_binary(
    name = "fitpriors",
    srcs = ["FitPriors.scala"],
    visibility = ["//visibility:public"],
    main_class = "rainier.models.FitPriors",
    deps = ["//src/compute", "//src/sampler", "//src/core",  "//src/report"]
)

scala_binary(
    name = "fithll",
    srcs = ["FitHLL.scala"],
    visibility = ["//visibility:public"],
    main_class = "rainier.models.FitHLL",
    deps = ["//src/compute", "//src/sampler", "//src/core", "hll"]
)

scala_library(
    name = "hll",
    srcs = ["HLL.scala"],
    visibility = ["//visibility:public"],
    deps = ["//src/compute", "//src/core"]
)
