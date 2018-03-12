#!/usr/bin/env python

import collections
import json
import os
import re
import shutil
import subprocess

BAZEL_CMD = "bazel build ... --aspects tools/intellij/intellij.bzl%intellij_aspect " \
            "--output_groups intellij-ide-info " \
            "--experimental_show_artifacts"

DEPS = ['module_deps', 'exports', 'lib_deps', 'targets']

MODULE_TEMPLATE = """\
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$/../../{source_root}">
      <sourceFolder url="file://$MODULE_DIR$/../../{source_root}" {is_test}/>
    </content>
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
    <orderEntry type="library" name="scala-sdk-2.11.8" level="application" />
    <orderEntry type="library" name="scalatest" level="project" />
{dependencies}
  </component>
</module>
"""
MODULES_TEMPLATE = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectModuleManager">
    <modules>
      <module fileurl="file:///$PROJECT_DIR$/.idea/project.iml"
      filepath="$PROJECT_DIR$/.idea/project.iml" />
{modules}
    </modules>
  </component>
</project>
"""
MODULE_ENTRY_TEMPLATE = '      <module fileurl="file://{dest}" filepath="{dest}" />'
LIBRARY_TEMPLATE = """\
<component name="libraryTable">
  <library name="{name}">
    <CLASSES>
      <root url="jar://{path}!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
"""
DEP_TEMPLATE = '    <orderEntry type="module" module-name="{}" />'
EXP_TEMPLATE = '    <orderEntry type="module" exported="" module-name="{}" />'
LIB_TEMPLATE = '    <orderEntry type="library" name="{}" level="project" />'
COMPILER_TEMPLATE = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <resourceExtensions />
    <wildcardResourcePatterns>
      <entry name="!?*.java" />
      <entry name="!?*.form" />
      <entry name="!?*.class" />
      <entry name="!?*.groovy" />
      <entry name="!?*.scala" />
      <entry name="!?*.flex" />
      <entry name="!?*.kt" />
      <entry name="!?*.clj" />
      <entry name="!?*.aj" />
    </wildcardResourcePatterns>
    <annotationProcessing>
      <profile default="true" name="Default" enabled="false">
        <processorPath useClasspath="true" />
      </profile>
    </annotationProcessing>
  </component>
</project>
"""
PROJECT_TEMPLATE = """\
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file:///$MODULE_DIR$">
      <excludeFolder url="file://$MODULE_DIR$/.idea" />
      <excludeFolder url="file://$MODULE_DIR$/bazel-bin" />
      <excludeFolder url="file://$MODULE_DIR$/bazel-genfiles" />
      <excludeFolder url="file://$MODULE_DIR$/bazel-out" />
      <excludeFolder url="file://$MODULE_DIR$/bazel-testlogs" />
      <excludeFolder url="file://$MODULE_DIR$/bazel-{project}" />
    </content>
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
    <orderEntry type="library" name="scala-sdk-2.11.8" level="application" />
  </component>
</module>
"""
SCALATEST_TEMPLATE = """\
<component name="libraryTable">
  <library name="scalatest">
    <CLASSES>
      <root url="jar://{output_base}/external/scalatest/scalatest_2.11-2.2.6.jar!/" />
      <root url="jar://{output_base}/external/scala/lib/scala-xml_2.11-1.0.4.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES />
  </library>
</component>
"""

LIB_PATH = re.compile(r'url="jar://\$PROJECT_DIR\$/([^!]+)!/"')
EXTERNAL_LIB = re.compile(r'/external/[^/]+/')


def module_name(source):
    return (source.replace('scala/com/stripe/', '').replace('src/main', '')
            .replace('src', '').replace('/', '-').strip('-'))


def write_file(target, content, **subs):
    with open(target, 'w') as fh:
        fh.write(content.format(**subs))


def write_libraries(libraries, output_base, output_path):
    if not os.path.exists(os.path.join('.idea', 'libraries-new')):
        os.makedirs(os.path.join('.idea', 'libraries-new'))

    for libfile in libraries:
        jsonbase = os.path.basename(libfile)
        base, ext = os.path.splitext(jsonbase)
        xmlfile = "%s.xml" % base
        libjson = open(libfile).read()
        lib = json.loads(libjson)
        path = find_jar(lib['path'], [output_base, output_path])
        if not path:
            print "WARNING: %s not found in any output path" % lib
            continue
        write_file(os.path.join('.idea', 'libraries-new', xmlfile), LIBRARY_TEMPLATE,
                   name=lib['name'], path=path)


def find_jar(path, roots):
    if os.path.exists(path):
        return "$PROJECT_DIR$/%s" % path
    else:
        for root in roots:
            if os.path.exists(os.path.join(root, path)):
                return "%s/%s" % (root, path)
    # munge the path because sometimes bazel seems to combine like jars:
    # example: path is bazel-out/local-fastbuild/bin/external/com_stripe_data_common/3rdparty
    # /jvm/org/objenesis/libobjenesis.jar
    # actual path is: bazel-out/local-fastbuild/bin/3rdparty/jvm/org/objenesis/libobjenesis.jar
    if EXTERNAL_LIB.search(path):
        return find_jar(EXTERNAL_LIB.sub("/", path), roots)
        # ... if we reach here, we didn't find it


def write_modules(modules):
    mdirs = collections.defaultdict(lambda: dict((d, set()) for d in DEPS))
    for module in modules:
        fcnt = open(module, 'r').read()
        info = json.loads(fcnt)
        for d in DEPS:
            mdirs[info['root']][d].update(info[d])

    # push module up the dir tree as far as possible
    reduced_mdirs = {}
    for source in mdirs:
        reduced = reduce(source, mdirs)
        reduced_mdirs[reduced] = mdirs[source]
    mdirs = reduced_mdirs

    dep_map = {}
    for source in mdirs:
        dep_map.update(dict((fqn, source)
                            for fqn in mdirs[source]['targets']))

    # for each module in mdir we need to output an .iml file
    # in that iml file we need to translate module deps into refs
    # to the modules we'll output, rather than to their original
    # targets. And add a library dep for each lib dep, etc.
    # We also want to remove redundant bits from the names of modules
    # so we need target -> source -> modulename
    # modulename.iml is the basename of the module file
    if not os.path.exists(os.path.join('.idea', 'modules-new')):
        os.makedirs(os.path.join('.idea', 'modules-new'))

    entries = []
    for source, info in mdirs.items():
        modname = module_name(source)
        modfile = os.path.join('.idea', 'modules-new', modname + '.iml')
        mod = {'source_root': source,
               'dependencies': "",
               'is_test': 'isTestSource="true" ' if (
                   modname.startswith('test-') or ('--test') in modname) else ''}
        deps = [DEP_TEMPLATE.format(module_name(dep_map[m])) for m in info['module_deps']]
        deps += [EXP_TEMPLATE.format(module_name(dep_map[m])) for m in info['exports']]
        deps += [LIB_TEMPLATE.format(m) for m in info['lib_deps']]
        mod['dependencies'] = "\n".join(deps)
        write_file(modfile, MODULE_TEMPLATE, **mod)

        entry = "$PROJECT_DIR$/.idea/modules/{}.iml".format(modname)
        entries.append(MODULE_ENTRY_TEMPLATE.format(dest=entry))
    write_file('.idea/modules-new.xml', MODULES_TEMPLATE, modules="\n".join(entries))


def reduce(dr, dir_hash):
    dirs = dir_hash.keys()
    dirs.remove(dr)
    return _reduce(dr, dirs)


def _reduce(dr, dirs):
    h, t = os.path.split(dr)
    for path in dirs:
        if path.startswith(h):
            return dr
    return _reduce(h, dirs)


def main():
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    print("Building project in {}...".format(root))
    os.chdir(root)
    subprocess.check_call("bazel build ...", shell=True)

    print("Setting up intellij...")
    output_base = subprocess.check_output("bazel info output_base", shell=True).strip()
    print("Output base: %s" % output_base)

    output_path = subprocess.check_output("bazel info output_path", shell=True).strip()
    print("Output path: %s" % output_path)

    print("Running bazel aspect...")
    out = subprocess.check_output(BAZEL_CMD, shell=True, stderr=subprocess.STDOUT)
    artifacts = [line[3:] for line in out.split("\n") if line.startswith(">>>")]
    libraries = [l for l in artifacts if 'library__' in l]
    modules = [m for m in artifacts if 'module__' in m]
    print "Writing libraries..."
    write_libraries(libraries, output_base, output_path)
    print "Writing modules..."
    write_modules(modules)
    print "Installing new config..."
    write_file('.idea/project.iml', PROJECT_TEMPLATE, project=os.path.basename(os.getcwd()))
    write_file('.idea/compiler.xml', COMPILER_TEMPLATE)
    write_file('.idea/libraries-new/scalatest.xml', SCALATEST_TEMPLATE, output_base=output_base)

    if os.path.exists('.idea/libraries'):
        shutil.rmtree('.idea/libraries')
    if os.path.exists('.idea/modules'):
        shutil.rmtree('.idea/modules')
    if os.path.exists('.idea/modules.xml'):
        os.unlink('.idea/modules.xml')
    os.rename('.idea/libraries-new', '.idea/libraries')
    os.rename('.idea/modules-new', '.idea/modules')
    os.rename('.idea/modules-new.xml', '.idea/modules.xml')
    print "Done!"


if __name__ == '__main__':
    main()
