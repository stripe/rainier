#/bin/sh

curl -Lo /tmp/coursier https://git.io/coursier-cli
chmod +x /tmp/coursier
/tmp/coursier bootstrap \
    -r jitpack \
    -i user -I user:sh.almond:scala-kernel-api_2.12.10:0.9.0 \
    sh.almond:scala-kernel_2.12.10:0.9.0 \
    -f -o /tmp/almond
/tmp/almond --install \
 -f --id rainier -N "Rainier (Scala 2.12)" \
 --extra-repository "https://dl.bintray.com/cibotech/public" \
 --extra-repository "https://dl.bintray.com/rainier/maven" \
 --extra-repository "https://jitpack.io" \
 --logo $PWD/logo.png
 