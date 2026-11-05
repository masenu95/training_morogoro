# AddUpdateProduct (port 7002)
# Needs MySQL, RabbitMQ, Eureka and ConfigServer running.
#
# Build the image (run from the AddUpdateProduct folder):
#   docker build -t training-add-update-product .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Option A: host network (recommended, every service works the same as in the IDE)
#   Docker Desktop: Settings > Resources > Network > Enable host networking, then restart Docker.
#   docker run -d --name add-update-product \
#     --network host \
#     training-add-update-product
#
# Option B: without host networking. host.docker.internal is your machine as seen from the
# container. Use this to run this service in Docker while the others run in the IDE.
#   docker run -d --name add-update-product \
#     -p 7002:7002 \
#     -e CONFIG_SERVER_URL=http://host.docker.internal:8888 \
#     -e "SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/training_products?createDatabaseIfNotExist=true" \
#     -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://host.docker.internal:8761/eureka/ \
#     -e MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
#     -e SPRING_RABBITMQ_HOST=host.docker.internal \
#     training-add-update-product
#
# Logs:    docker logs -f add-update-product
# Stop:    docker stop add-update-product
# Remove:  docker rm add-update-product
# After code changes: docker rm -f add-update-product, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
