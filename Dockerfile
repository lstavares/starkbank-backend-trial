FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY . .
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN useradd --system --create-home --uid 10001 app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
