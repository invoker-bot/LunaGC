ARG GRADLE_IMAGE=gradle:8.5.0-jdk17
ARG JRE_IMAGE=eclipse-temurin:17-jre

FROM ${GRADLE_IMAGE} AS build
USER root
WORKDIR /build

COPY build.gradle settings.gradle ./
COPY lib/ ./lib/
COPY src/ ./src/

RUN --mount=type=cache,target=/root/.gradle \
    gradle jar -PskipHandbook=1 --no-daemon --console=plain

FROM ${JRE_IMAGE}
WORKDIR /opt/lunagc

COPY --from=build /build/LunaGC-7.1.0.jar ./LunaGC.jar
COPY resources/ ./resources/

# A submodule checkout without Git LFS contains tiny pointer files, not game data.
RUN set -eu; \
    test -d resources/BinOutput; \
    test -d resources/ExcelBinOutput; \
    test -d resources/Scripts; \
    test -d resources/TextMap; \
    pointers="$(find resources -type f -size -1024c \
        -exec grep -l '^version https://git-lfs.github.com/spec/v1' {} + || true)"; \
    if [ -n "$pointers" ]; then \
        printf 'Git LFS files are not checked out:\n%s\nRun: git -C resources lfs pull\n' "$pointers" >&2; \
        exit 1; \
    fi; \
    groupadd --system lunagc; \
    useradd --system --gid lunagc --home /var/lib/lunagc lunagc; \
    mkdir -p /var/lib/lunagc; \
    chown lunagc:lunagc /var/lib/lunagc

ENV LUNAGC_RESOURCES_DIR=/opt/lunagc/resources
WORKDIR /var/lib/lunagc
USER lunagc

EXPOSE 8088/tcp 22101/udp
ENTRYPOINT ["java", "-jar", "/opt/lunagc/LunaGC.jar"]
