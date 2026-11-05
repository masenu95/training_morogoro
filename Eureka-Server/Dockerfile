# Eureka-Server (port 8761)
# Start this first. Open http://localhost:8761 to see registered services.
#
# Build the image (run from the Eureka-Server folder):
#   docker build -t training-eureka-server .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Option A: host network (recommended, every service works the same as in the IDE)
#   Docker Desktop: Settings > Resources > Network > Enable host networking, then restart Docker.
#   docker run -d --name eureka-server \
#     --network host \
#     training-eureka-server
#
# Option B: without host networking. host.docker.internal is your machine as seen from the
# container. Use this to run this service in Docker while the others run in the IDE.
#   docker run -d --name eureka-server \
#     -p 8761:8761 \
#     -e MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
#     training-eureka-server
#
# Logs:    docker logs -f eureka-server
# Stop:    docker stop eureka-server
# Remove:  docker rm eureka-server
# After code changes: docker rm -f eureka-server, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
