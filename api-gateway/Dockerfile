# api-gateway (port 8080)
# Routes requests to services found in Eureka.
#
# Build the image (run from the api-gateway folder):
#   docker build -t training-api-gateway .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Option A: host network (recommended, every service works the same as in the IDE)
#   Docker Desktop: Settings > Resources > Network > Enable host networking, then restart Docker.
#   docker run -d --name api-gateway \
#     --network host \
#     -v /home/mukhusin-siraji/Dev/training/training_morogoro/api-gateway/logs:/home/mukhusin-siraji/Dev/training/training_morogoro/api-gateway/logs \
#     training-api-gateway
#
# The gateway reaches services at the addresses they register in Eureka (localhost:port),
# so run it with Option A, or keep it in the IDE.
#
# Logs:    docker logs -f api-gateway
# Stop:    docker stop api-gateway
# Remove:  docker rm api-gateway
# After code changes: docker rm -f api-gateway, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
