FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
ARG MAVEN_BUILD_HEAP_MB=384
WORKDIR /workspace

ENV MAVEN_OPTS="-Xmx${MAVEN_BUILD_HEAP_MB}m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

COPY bourse-azma-api/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests dependency:go-offline

COPY bourse-azma-api/src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package && \
    JAR_FILE="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' | head -n 1)" && \
    test -n "${JAR_FILE}" && \
    cp "${JAR_FILE}" /workspace/app.jar && \
    java -Djarmode=tools -jar /workspace/app.jar extract --layers --launcher --destination /workspace/extracted && \
    "${JAVA_HOME}/bin/jlink" \
    --add-modules java.base,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.security.jgss,java.sql,java.xml,jdk.charsets,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.management,jdk.net,jdk.unsupported,jdk.zipfs \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-6 \
    --output /opt/java-minimal

FROM alpine:3.21
WORKDIR /app

RUN addgroup -S app && adduser -S -G app -u 10001 app && \
    apk add --no-cache libstdc++ tini netcat-openbsd

ENV JAVA_TOOL_OPTIONS="\
-XX:+UseSerialGC \
-XX:+UseContainerSupport \
-Xms48m \
-Xmx112m \
-Xss256k \
-XX:MaxMetaspaceSize=128m \
-XX:MaxDirectMemorySize=16m \
-XX:TieredStopAtLevel=1 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom \
-Dfile.encoding=UTF-8"

COPY --from=build /opt/java-minimal /opt/java
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/application/ ./
COPY --chown=10001:10001 --chmod=0444 market.csv /app/market.csv

EXPOSE 9003

USER 10001:10001

ENTRYPOINT ["/sbin/tini", "--", "/opt/java/bin/java", "org.springframework.boot.loader.launch.JarLauncher"]
