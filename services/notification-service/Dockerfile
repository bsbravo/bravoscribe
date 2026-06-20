# Multi-stage build — see SYSTEM_SPEC.md §7.1 for full template explanation

# Stage 1 — Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# Stage 2 — Runtime (~150MB, no build tools or source code)
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.5.4/applicationinsights-agent-3.5.4.jar applicationinsights-agent.jar
EXPOSE 8083
ENTRYPOINT ["java", "-javaagent:applicationinsights-agent.jar", "-jar", "app.jar"]
