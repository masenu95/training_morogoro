# OrderService (port 8089)
# Needs MySQL, RabbitMQ, Eureka and ConfigServer running.
#
# Build the image (run from the OrderService folder):
#   docker build -t training-order-service .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Option A: host network (recommended, every service works the same as in the IDE)
#   Docker Desktop: Settings > Resources > Network > Enable host networking, then restart Docker.
#   docker run -d --name order-service \
#     --network host \
#     training-order-service
#
#   Second instance on port 8090:
#   docker run -d --name order-service-2 --network host -e SERVER_PORT=8090 training-order-service
#
# Option B: without host networking. host.docker.internal is your machine as seen from the
# container. Use this to run this service in Docker while the others run in the IDE.
#   docker run -d --name order-service \
#     -p 8089:8089 \
#     -e CONFIG_SERVER_URL=http://host.docker.internal:8888 \
#     -e "SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/order_db?createDatabaseIfNotExist=true" \
#     -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://host.docker.internal:8761/eureka/ \
#     -e MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
#     -e SPRING_RABBITMQ_HOST=host.docker.internal \
#     training-order-service
#
# Logs:    docker logs -f order-service
# Stop:    docker stop order-service
# Remove:  docker rm order-service
# After code changes: docker rm -f order-service, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
