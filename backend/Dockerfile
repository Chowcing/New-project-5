FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Xmx512m -XX:MaxRAMPercentage=70"
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG EXPENSE_DEPLOYMENT_VERSION=local-dev
ENV EXPENSE_DEPLOYMENT_VERSION=$EXPENSE_DEPLOYMENT_VERSION
COPY --from=build /workspace/target/expense-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
