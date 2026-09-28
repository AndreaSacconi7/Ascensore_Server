# Build the jar
FROM maven:3-eclipse-temurin-24 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q package -DskipTests

# Run it on a JRE
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home ascensore
COPY --from=build /app/target/*.jar app.jar
USER ascensore
# Heap sized from the machine's memory, leaving room for the rest of the JVM; restart rather than limp on
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
