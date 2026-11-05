# FetchAllProduct (port 7009)
# Needs MySQL, Eureka and ConfigServer running.
#
# Build the image (run from the FetchAllProduct folder):
#   docker build -t training-fetch-product-service .
#
# The first build downloads all Maven dependencies (about 10 minutes). Later builds of any
# service reuse the shared Maven cache and are much faster.
#
# Option A: host network (recommended, every service works the same as in the IDE)
#   Docker Desktop: Settings > Resources > Network > Enable host networking, then restart Docker.
#   docker run -d --name fetch-product-service \
#     --network host \
#     training-fetch-product-service
#
#   Second instance on port 7010:
#   docker run -d --name fetch-product-service-2 --network host -e SERVER_PORT=7010 training-fetch-product-service
#
# Option B: without host networking. host.docker.internal is your machine as seen from the
# container. Use this to run this service in Docker while the others run in the IDE.
#   docker run -d --name fetch-product-service \
#     -p 7009:7009 \
#     -e CONFIG_SERVER_URL=http://host.docker.internal:8888 \
#     -e "SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/training_products?createDatabaseIfNotExist=true" \
#     -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://host.docker.internal:8761/eureka/ \
#     -e MANAGEMENT_ZIPKIN_TRACING_ENDPOINT=http://host.docker.internal:9411/api/v2/spans \
#     training-fetch-product-service
#
# Logs:    docker logs -f fetch-product-service
# Stop:    docker stop fetch-product-service
# Remove:  docker rm fetch-product-service
# After code changes: docker rm -f fetch-product-service, then build and run again.

FROM maven:3.9-eclipse-temurin-21-alpine
LABEL authors="mukhusin-siraji"

WORKDIR /app

COPY . .

RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests

ENTRYPOINT ["sh", "-c", "java -jar target/*.jar"]
