#!/bin/sh
set -eu
if [ -n "${NATIVE_PROVIDER_LIBRARY:-}" ]; then
    test -r "$NATIVE_PROVIDER_LIBRARY"
    exec java "-Dreactor.dubbo.native.path=$NATIVE_PROVIDER_LIBRARY" -cp '/app/app.jar:/app/lib/*' "$SAMPLE_MAIN_CLASS"
fi
exec java -cp '/app/app.jar:/app/lib/*' "$SAMPLE_MAIN_CLASS"
