# syntax=docker/dockerfile:1.7
# Workspace-root build context; prepare the selected checkout's package + runtime
# dependency directory first (verify-compose-topology --prepare / -Prepare).
FROM busybox:1.37 AS artifacts
ARG SAMPLE_DIR
ARG ARTIFACT_DIR=target
COPY ${SAMPLE_DIR}/${ARTIFACT_DIR}/*.jar /input/
COPY ${SAMPLE_DIR}/${ARTIFACT_DIR}/dependency/*.jar /output/lib/
RUN set -eu; count=0; \
    for jar in /input/*.jar; do \
      case "$jar" in */original-*|*-sources.jar|*-javadoc.jar|*-tests.jar) continue ;; esac; \
      cp "$jar" /output/app.jar; count=$((count + 1)); \
    done; \
    test "$count" -eq 1

FROM ibm-semeru-runtimes:open-21-jre AS runtime
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl redis-tools \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app/.reactor/native /app/.java-rust-cache/native /app/.java-rust-dubbo/native \
    && chown -R app:app /app
WORKDIR /app
ARG MAIN_CLASS
ENV SAMPLE_MAIN_CLASS=${MAIN_CLASS} \
    JAVA_TOOL_OPTIONS="-Xms16m -Xmx48m -Xss256k -Xgc:threads=1 -XX:ActiveProcessorCount=1 -Xtune:virtualized -Xdump:none" \
    LANG=C.UTF-8
COPY --from=artifacts --chown=app:app /output/ /app/
COPY --chmod=755 rest-sample-cache-reader/docker/compose/container-check.sh /app/container-check.sh
COPY --chmod=755 rest-sample-cache-reader/docker/compose/start.sh /app/start.sh
USER 10001:10001
ENTRYPOINT ["/app/start.sh"]

FROM runtime AS native-provider
# Explicit accepted ABI-3 library required by NativeProviderApplication. Neither
# bytes nor per-platform provenance are modified or relabelled by this image.
COPY --chown=app:app java-rust-dubbo-spring-boot/java-rust-dubbo-native-linux-x64/src/main/resources/native/ /app/native/
ENV NATIVE_PROVIDER_LIBRARY=/app/native/librust_dubbo-linux-x64.so
