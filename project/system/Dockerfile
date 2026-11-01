# Images (normally built by ../compose.yaml):
#   docker build -t alert-service .                         the service (default target)
#   docker build --target demo -t alert-service-demo .      the demo market data adapter

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon --quiet bootJar -x test && cp build/libs/*.jar app.jar

FROM build AS demo-build
RUN ./gradlew --no-daemon --quiet installDemoAdapter

# Demo adapter: plays scenarios on ZeroMQ, with its page on 5557 (see DemoAdapter). Binds all interfaces inside the container.
FROM eclipse-temurin:25-jre AS demo
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=demo-build /workspace/build/demo-adapter/lib lib
USER app
EXPOSE 5556 5557
ENTRYPOINT ["java", "-cp", "/app/lib:/app/lib/*", "com.alert_service.demo.DemoAdapter"]
CMD ["--endpoint", "tcp://*:5556"]

# Service (default target, keep it last).
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

# Inside the container the server must listen on all interfaces; keep it private by publishing the port on the
# host's loopback only (-p 127.0.0.1:8080:8080). Adapters on the host are reached through host.docker.internal.
ENV SERVER_ADDRESS=0.0.0.0 \
    ALERT_ZMQ_ENDPOINTS=tcp://host.docker.internal:5556
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
