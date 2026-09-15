FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
COPY lib lib
COPY src src
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl \
    && addgroup -S schwab && adduser -S schwab -G schwab \
    && mkdir -p /app/workbench \
    && chown -R schwab:schwab /app
COPY --from=build /workspace/target/agentic-url-shortener-1.0.0.jar app.jar
USER schwab
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=5s --start-period=90s --retries=8 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
