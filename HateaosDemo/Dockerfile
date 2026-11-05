# HateaosDemo (port 8077)
# Standalone demo, needs nothing else. Try http://localhost:8077/employees-hateaos/1
#
# Build the image (run from the HateaosDemo folder):
#   docker build -t spring-app-hateaos-demo .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Run:
#   docker run -d --name hateaos-demo \
#     -p 8077:8077 \
#     spring-app-hateaos-demo
#
# Logs:    docker logs -f hateaos-demo
# Stop:    docker stop hateaos-demo
# Remove:  docker rm hateaos-demo
# After code changes: docker rm -f hateaos-demo, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
