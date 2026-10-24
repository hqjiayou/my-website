# syntax=docker/dockerfile:1
# 四个 Java 服务共用本文件。构建 context 是仓库根。
# MODULE 例：gateway、auth、admin、site/site-app
# JAR_GLOB 相对本文件 COPY 进来的 website-services 根，例：auth/target/auth-*.jar

FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /src
COPY services/website-services/ ./

ARG MODULE
ARG JAR_GLOB
RUN set -eu; \
    test -n "${MODULE}"; \
    test -n "${JAR_GLOB}"; \
    mvn -pl "${MODULE}" -am -B package -DskipTests; \
    mkdir -p /out; \
    cp ${JAR_GLOB} /out/app.jar; \
    test -s /out/app.jar

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app \
    && mkdir -p /app \
    && chown app:app /app

WORKDIR /app
COPY --from=builder --chown=app:app /out/app.jar /app/app.jar

ARG EXPOSE_PORT=8080
EXPOSE ${EXPOSE_PORT}

USER app
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
