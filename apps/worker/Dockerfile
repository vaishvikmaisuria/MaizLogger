FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY apps/worker/ apps/worker/
RUN chmod +x gradlew && ./gradlew :apps:worker:bootJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/apps/worker/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
