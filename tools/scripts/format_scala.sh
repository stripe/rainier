#!/bin/bash

# fail when any basic command exits with a non-zero status.
set -e

# do some convulted tricks to figure out where our repo's root is. in
# practice this is likely equivalent to "$HOME/stripe/$PROJNAME".
REPO_ROOT=`git rev-parse --show-toplevel`

COURSIER_PATH="$REPO_ROOT/tools/coursier"
SCALAFMT_PATH="$REPO_ROOT/tools/scalafmt"

# display usage information.
usage() {
    PROG=$(basename $0)
    echo "usage: $PROG [-a | -h | -m | -p | -t ] [FILES...]"
    echo ""
    echo "options (use only one of these):"
    echo "  -a: format all files in-place"
    echo "  -h: show this message and exit"
    echo "  -m: format files that differ from master in-place (default)"
    echo "  -p: format cached (precommit) files in-place"
    echo "  -t: test mode, verify that all files are formatted"
    echo ""
    echo "arguments: if given, only format these files"
    echo ""
}

# set up some variables we'll use to figure out what mode to run in.
MODE="modern"  # will be "legacy" or "modern"
OPT="-i"       # will be "--test" or "-i"
PRECOMMIT=""   # will be "true" or empty

# see if we got an optional argument.
case "$1" in
    -a) MODE="legacy"; OPT="-i"; shift;;
    -h) usage; exit 0;;
    -m) MODE="modern"; OPT="-i"; shift;;
    -p) MODE="modern"; OPT="-i"; PRECOMMIT="true"; shift;;
    -t) MODE="legacy"; OPT="--test"; shift;;
    *)  MODE="modern"; OPT="-i";; # don't shift, might be an argument
esac

# save the remaining arguments we got into ARGS.
ARGS="$@"

# move to the repo's base directory.
cd $REPO_ROOT

# destroy old generated files cached by mvn plugin, if any.
find $REPO_ROOT -type d -name "*generated-*" -prune -exec rm -r "{}" \;

# if we were called with args, or with --test, we want to run in the
# original, legacy mode. this means we'll read *all* the files from
# the FS unless an explicit list was given.
if ([ -n "$ARGS" ] || [ $MODE == "legacy" ]); then

    # legacy mode -- format all files (or specified files)

    # if no args were given, find all subdirectories.
    if [ -z "$ARGS" ]; then
        ARGS=$(ls -d src | tr "\n" ",")
    fi

    # this is the legacy code path
    #set -x
    java -jar $SCALAFMT_PATH $OPT --quiet -f "$ARGS"
    RES=$?
    echo ""; echo "" # preserve scalafmt's output
    if [ $RES -ne 0 ]; then
        echo -e "\e[1;33;41mScala formatting failed."
        echo -e "\e[0m Run \e[1;32m./tools/scripts/format_scala.sh\e[0m to fix it."
        exit 1
    else
        exit 0
    fi

else

    # modern mode -- detect which files to format

    # decide if we are in "precommit" mode or not.
    if [ -n "$PRECOMMIT" ]; then
        # in this case we only want to format files that were committed.
        GITCMD="git diff --cached --name-only --diff-filter=ACM"
    else
        # interactive mode: we want to format everything that is different
        # from master.
        GITCMD="git diff --name-only --diff-filter=ACM origin/master"
    fi

    # use git, grep, and awk to display how many files were matched, and
    # construct the optional command to run to format them. the returned
    # string will be empty if no files matched.
    CMD=$($GITCMD | grep '.scala$' | awk  -v fmt="$SCALAFMT_PATH" '
NR == 1 { args = $0 }
NR > 1 { args = args "," $0 }
END {
  if (NR > 0) {
    print "formatting " NR " records:" > "/dev/stderr"
    print "java -jar " fmt " -i --files " args
  } else {
    print "no records to format." > "/dev/stderr"
  }
}
')

    # run the command we got, if any. this is where the actual formatting
    # happens, when there are changed files.
    if [ -n "$CMD" ]; then
        exec $CMD
    else
        exit 0
    fi
fi
