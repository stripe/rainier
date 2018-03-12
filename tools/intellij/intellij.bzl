# This is an aspect for generating an IntelliJ project structure and dependency
# tree.
#
# It generates json data files for two types of objects:
#  - "modules", which are a unit of compilation and have sources
#  - "libraries", each of which is a symlink to a jar.
#
# The json files are then collated by tools/intellij/setup.py and used to
# generate iml files for the modules and libraries.

_module_kinds = [
    "java_library",
    "java_test",
    "java_binary",
    "scala_library",
    "scala_macro_library",
    "scala_binary",
    "scala_test"
]


def _intellij_name(label):
    """
    Converts a label to a IntelliJ module or library name, mostly by
    converting special characters to underscores.

    >>> _intellij_name("@foo//bar/baz:baz")
    "foo_bar_baz"
    """
    s = str(label)

    # Strip the target name if it matches the package name (eg //foo/bar:bar)
    if label.package.split("/")[-1] == label.name:
        s = s[:-(len(label.name) + 1)]

    # As a special case, strip `//jar` from java_imports:
    if s.endswith("//jar"):
        s = s[:-5]
    elif s.endswith("//jar:file"):
        s = s[:-10]

    return s.replace("@", "").replace("//", "_").replace("/", "_").replace(":", "_")


def _common_source_root(sources):
    """
    Given a list of File objects, returns the highest source path which contains
    all of them. This isn't guaranteed to be unique, but we generally use just
    `srcs = glob(["*"])`, and in that common case this will pick up the folder
    where the BUILD file is.
    """
    common = None
    for src in sources:
        for file in src.files:
            path = file.dirname
            if common == None:
                common = path
            else:
                for i in range(len(common), 0):
                    if path.startswith(common[:i]):
                        common = common[:i]
                        break

    return common


def _create_module(ctx, target, libraries):
    """
    Creates a "module" by outputting an iml file for it. Any libraries that the
    module depends on need to be passed in, but dependency *modules* are
    collected from deps.
    """
    module = ctx.new_file("module__{}.json".format(_intellij_name(target.label)))

    dependency_modules = set()
    if hasattr(ctx.rule.attr, "deps"):
        exported_module_deps = set()
        for dep in getattr(ctx.rule.attr, "exports", []):
            if hasattr(dep, "intellij") and dep.intellij.module:
                exported_module_deps += set([_intellij_name(dep.label)])

        for dep in ctx.rule.attr.deps:
            if hasattr(dep, "intellij") and dep.intellij.module:
                dependency_modules += set([_intellij_name(dep.label)])

    module_deps = [str(m) for m in dependency_modules if m not in exported_module_deps]
    exports = [str(m) for m in dependency_modules if m in exported_module_deps]
    lib_deps = [str(l) for l in libraries]
    ctx.file_action(
        output=module,
        content=struct(
            root=_common_source_root(ctx.rule.attr.srcs),
            targets=[_intellij_name(target.label)],
            module_deps=module_deps,
            lib_deps=lib_deps,
            exports=exports
        ).to_json(),
        executable=False
    )

    return set([module])


def _create_library(ctx, target, jar):
    """
    Creates a "library" by producing an xml file for it. We only ever link one
    jar at a time (the File for which is passed in).
    """
    name = _intellij_name(target.label)
    library = ctx.new_file("library__{}.json".format(name))
    ctx.file_action(
        output=library,
        content=struct(
            name=name,
            path=jar.path,
            root=jar.root.path,
            basename=jar.basename,
            dirname=jar.dirname,
            short_path=jar.short_path
        ).to_json(),
        executable=False
    )

    return set([library])


def _intellij_aspect_impl(target, ctx):
    # Modules can depend on both other modules and libraries, but libraries
    # can't have dependencies themselves. So the aspect works two ways; by
    # taking an action for every node in the graph, it generates all the XML,
    # and by running recursively over the graph, it collects transitive jars -
    # saved in the 'transitive_library_deps' provider. Finally, bazel expects us
    # to collect outputs transitively in an "output group", so we do that with a
    # provider, too, named 'transitive_xml_outputs'.
    transitive_xml_outputs = set()
    transitive_library_deps = set()
    for attr_name in ["deps", "runtime_deps", "exports"]:
        if hasattr(ctx.rule.attr, attr_name):
            for dep in getattr(ctx.rule.attr, attr_name):
                if hasattr(dep, "intellij"):
                    transitive_xml_outputs += dep.intellij.transitive_xml_outputs
                    transitive_library_deps += dep.intellij.transitive_library_deps

    is_module = False
    if hasattr(target, "java") or hasattr(target, "scala"):
        # For any packages with scala or java sources in this repository, create
        # a 'module'.
        if ctx.rule.kind in _module_kinds and ctx.rule.attr.srcs and not str(target.label).startswith("@"):
            transitive_xml_outputs += _create_module(ctx, target, transitive_library_deps)
            is_module = True
        # Then we add an IntelliJ 'library' for anything else. This should cover
        # java_imports, thrift, external source deps like parquet_scalding, etc.
        elif hasattr(target, "scala") and target.scala.outputs:
            transitive_xml_outputs += _create_library(ctx, target, target.scala.outputs.class_jar)
            transitive_library_deps += set([_intellij_name(target.label)])
        elif hasattr(target, "java"):
            for jar in target.java.outputs.jars:
                if jar.class_jar:
                    transitive_xml_outputs += _create_library(ctx, target, jar.class_jar)
                    transitive_library_deps += set([_intellij_name(target.label)])
    # This covers the macro jar case. See
    # https://github.com/bazelbuild/rules_scala/blob/e9c10ca80d3da00b535af47045e6e82b894d14df/scala/scala.bzl#L98-L100
    elif hasattr(target, "files"):
        for file in target.files:
            if file.basename.endswith(".jar"):
                transitive_xml_outputs += _create_library(ctx, target, file)
                transitive_library_deps += set([_intellij_name(target.label)])

    return struct(
        intellij=struct(
            module=is_module,
            transitive_xml_outputs=transitive_xml_outputs,
            transitive_library_deps=transitive_library_deps
        ),
        output_groups={"intellij-ide-info": transitive_xml_outputs}
    )


intellij_aspect = aspect(
    implementation = _intellij_aspect_impl,
    attr_aspects = ["deps", "runtime_deps", "exports"]
)
