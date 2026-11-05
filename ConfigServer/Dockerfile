# ConfigServer (port 8888)
# Start after Eureka. Check: curl http://localhost:8888/order-service/default
#
# Build the image (run from the ConfigServer folder):
#   docker build -t training-config-server .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Option A: host network (recommended, every service works the same as in the IDE)
#   Docker Desktop: Settings > Resources > Network > Enable host networking, then restart Docker.
#   docker run -d --name config-server \
#     --network host \
#     -v /home/mukhusin-siraji/Dev/training/training_morogoro/config-files:/home/mukhusin-siraji/Dev/training/training_morogoro/config-files \
#     training-config-server
#
# Option B: without host networking. host.docker.internal is your machine as seen from the
# container. Use this to run this service in Docker while the others run in the IDE.
#   docker run -d --name config-server \
#     -p 8888:8888 \
#     -v /home/mukhusin-siraji/Dev/training/training_morogoro/config-files:/home/mukhusin-siraji/Dev/training/training_morogoro/config-files \
#     training-config-server
#
# Logs:    docker logs -f config-server
# Stop:    docker stop config-server
# Remove:  docker rm config-server
# After code changes: docker rm -f config-server, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
