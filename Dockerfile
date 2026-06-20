# Stage 1: Build
FROM gradle:9.5.1-jdk25 AS builder
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon
COPY src ./src
RUN gradle bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# docker build -t auto-scale-app .
# docker run -d -p 8080:8080 --name auto-scale-app auto-scale-app
# docker run  -p 8080:8080 --name auto-scale-app auto-scale-app