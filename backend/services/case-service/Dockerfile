FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace
COPY --from=events . /events
RUN mvn -q -DskipTests -f /events/pom.xml install
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/target/case-service-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
