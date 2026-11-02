# training_morogoro

Four Spring Boot services for managing products, inventory and orders, plus a Eureka server and an API gateway. Each service uses Spring Data JPA with MySQL and exposes a REST API. Services register with Eureka and call each other by service name with OpenFeign clients (`@FeignClient`). Clients such as Postman go through the gateway, which logs every request. OrderService is protected with Resilience4j: circuit breaker, retry, rate limiter, bulkhead and time limiter.

## Services

| Service | Eureka name | Port | Database | Responsibility |
|---|---|---|---|---|
| Eureka-Server | netflix-eureka-server | 8761 | - | Service registry |
| api-gateway | api-gateway | 8080 | - | Single entry point, routes requests through Eureka and logs them |
| AddUpdateProduct | add-update-product | 7002 | training_products | Add and update products, and seed 12 sample products on startup |
| FetchAllProduct | fetch-product-service | 7009, 7010 | training_products | Fetch all products, a product by id, or products by name |
| OrderService | order-service | 8089, 8090 | order_db | Place, cancel and list orders and order items |
| ProductInvetory | inventory-service | 7004 | inventory_db | Record stock ADD/DEDUCT actions with opening and closing quantities |

AddUpdateProduct and FetchAllProduct share the `products` table in `training_products`. A product added through AddUpdateProduct can be read through FetchAllProduct.

## API gateway

All client requests go to the gateway on port 8080. The first path segment is the Eureka service name. The gateway removes it and forwards the rest of the path to that service, load balanced through Eureka.

| Gateway URL | Forwarded to |
|---|---|
| `localhost:8080/add-update-product/products` | add-update-product `/products` |
| `localhost:8080/fetch-product-service/products` | fetch-product-service `/products` |
| `localhost:8080/order-service/orders` | order-service `/orders` |
| `localhost:8080/inventory-service/inventory` | inventory-service `/inventory` |

Routes are defined in `api-gateway/src/main/java/com/mukhusin/apigateway/config/GatewayRoutesConfig.java` with `route`, `stripPrefix(1)` and `lb(serviceName)`.

The gateway also has a logging filter that writes one line when a request arrives and one when the response leaves, with the status and time taken:

```
Incoming: POST /order-service/orders
Outgoing: /order-service/orders status=201 CREATED time=184 ms
```

The logs are shown in the console and also saved to `api-gateway/logs/api-gateway.log`. A new file starts every 10 MB, and files older than 7 days are deleted:

```
logging.file.name=logs/api-gateway.log
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=7
```

The `logs/` folder is not tracked by git.

## Multiple instances

FetchAllProduct and OrderService get the most traffic, so each runs two instances on different ports.

| Service | Instance 1 | Instance 2 |
|---|---|---|
| fetch-product-service | 7009 | 7010 |
| order-service | 8089 | 8090 |

Each instance registers in Eureka with its own id, set in `application.properties`:

```
eureka.instance.instance-id=${spring.application.name}:${server.port}
```

Start the second instance on another port:

```
sh mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=7010
sh mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

The gateway (`lb()`) and the Feign clients get the list of instances from Eureka and share requests between them. If one instance stops, requests go to the other one. Both instances use the same database, so the data is the same.

The rate limiter and bulkhead count per instance, so with two OrderService instances the system accepts up to 10 orders per second.

## Resilience in OrderService

OrderService uses Resilience4j annotations on its endpoints. When a protection is triggered, the endpoint returns `503 Service Unavailable` from its fallback method instead of an error.

| Pattern | Instance | Used on | Setting |
|---|---|---|---|
| Circuit breaker | `orderService` | Place order, cancel order | Opens when 50% of the last 10 calls fail, and stays open for 10 seconds before testing again |
| Retry | `orderRetry` | Cancel order | Up to 3 attempts, 10 seconds apart |
| Rate limiter | `orderRateLimiter` | Place order | At most 5 requests per second, and extra requests are rejected straight away |
| Bulkhead | `orderBulkhead` | Place order | At most 3 orders processed at the same time, and others wait up to 20 ms |
| Time limiter | `orderTimeLimiter` | Configured | Calls that take longer than 2 seconds are cancelled |

Fallback responses:

| Endpoint | Status | Body |
|---|---|---|
| `POST /orders` | 503 | `Product or Inventory service unavailable. Reason: ...` |
| `PUT /orders/{id}/cancel` | 503 | `Unable to cancel order right now. Service unavailable.` |

Actuator endpoints are exposed under `/just-me` on OrderService, for example `localhost:8089/just-me/health` and `localhost:8089/just-me/circuitbreakers`.

## Communication

```
                Postman ──▶ api-gateway :8080 ──▶ any service (via Eureka)

                          Eureka-Server :8761
                     (every service registers here)

AddUpdateProduct :7002 ──writes──▶ [training_products] ◀──reads── FetchAllProduct :7009
                                                                    ▲          ▲
                                                       GET /products│          │GET /products
                                                  GET /products/{id}│          │
                                                                    │          │
                               OrderService :8089 ──────────────────┘          │
                                      │                                        │
                                      │ GET  /inventory/available/{productId}  │
                                      │ POST /inventory/deduct                 │
                                      │ POST /inventory/add                    │
                                      ▼                                        │
                               ProductInvetory :7004 ──────────────────────────┘
```

Feign clients look up the target by its Eureka name, for example `@FeignClient(name = "inventory-service")`. The name must match the target's `spring.application.name`.

| Caller | Called service | Endpoint | When |
|---|---|---|---|
| OrderService | fetch-product-service | `GET /products/{id}` | Placing an order, to get the product price |
| OrderService | inventory-service | `GET /inventory/available/{productId}` | Placing an order, to check stock |
| OrderService | inventory-service | `POST /inventory/deduct` | Placing an order, to reduce stock |
| OrderService | inventory-service | `POST /inventory/add` | Cancelling an order, to return stock |
| OrderService | fetch-product-service | `GET /products` | Listing orders, to show product names on order items |
| ProductInvetory | fetch-product-service | `GET /products` | Listing inventory and product stock, to show product names |

If FetchAllProduct is down, `GET /orders` and `GET /inventory` still return, but `productName` is `null`. `GET /inventory/products` fails, because it needs the product list.

### Place order flow

1. Postman sends `POST localhost:8080/order-service/orders` with `{"productId":1,"quantity":2}`. The gateway forwards it to OrderService.
2. OrderService gets the product from FetchAllProduct. If the product is not found, it returns `404`.
3. OrderService gets the available quantity from ProductInvetory. If the quantity is too low, it returns `400`.
4. OrderService asks ProductInvetory to deduct the quantity. ProductInvetory saves a DEDUCT record.
5. OrderService saves an `Order` with status `PLACED`, and an `OrderItem` with the price and total amount.
6. If there are too many requests, too many orders at once, or the circuit is open, OrderService returns `503` from its fallback.

### Cancel order flow

1. Postman sends `PUT localhost:8080/order-service/orders/{id}/cancel`.
2. For each order item, OrderService asks ProductInvetory to add the quantity back. ProductInvetory saves an ADD record.
3. OrderService sets the order status to `CANCELLED`.
4. If the inventory call fails, it is retried up to 3 times before the fallback returns `503`.

## Patterns used

| Group | Pattern | Where |
|---|---|---|
| Design | Decompose by business capability | Products, inventory and orders are separate services |
| Design | Database per service | order_db, inventory_db, training_products |
| Design | Command/query split | AddUpdateProduct writes, FetchAllProduct reads |
| Communication | API Gateway | Single entry point on 8080 with routing and logging |
| Communication | Service registry and discovery | Eureka, services call each other by name |
| Communication | Synchronous REST with a declarative client | OpenFeign |
| Communication | Client-side load balancing | Gateway `lb()` and Feign through Eureka |
| Communication | Multiple instances per service | FetchAllProduct and OrderService |
| Reliability | Circuit breaker with fallback | OrderService and inventory-service |
| Reliability | Retry | Cancel order |
| Reliability | Rate limiter | Place order and FetchAllProduct |
| Reliability | Bulkhead | Place order |
| Reliability | Time limiter | Configured in OrderService, not yet applied to an endpoint |
| Observability | Distributed tracing | Zipkin |
| Observability | Centralized request logging | Gateway, saved to `logs/api-gateway.log` |
| Observability | Health check and metrics | Spring Boot Actuator |

Not yet in place:

- AddUpdateProduct and FetchAllProduct share the `training_products` database.
- Placing an order deducts stock and then saves the order. There is no Saga, so if saving fails the stock is not returned automatically.
- There is no config server. Each service has its own local `application.properties`.

## API reference

Error responses are plain text, for example `Product not found`.

Each section shows the gateway prefix and the direct port. For example, `GET /products` on FetchAllProduct is `localhost:8080/fetch-product-service/products` through the gateway, or `localhost:7009/products` directly.

### AddUpdateProduct (gateway `localhost:8080/add-update-product`, direct `localhost:7002`)

#### Add product

`POST /products`

Request:
```json
{"name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1500000}
```

Response `201 Created`:
```json
{"id": 1, "name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1500000.0}
```

#### Update product

`PUT /products/{id}`, for example `PUT /products/1`

Request:
```json
{"name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1400000}
```

Response `200 OK`:
```json
{"id": 1, "name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1400000.0}
```

Response `404 Not Found`: `Product not found`

### FetchAllProduct (gateway `localhost:8080/fetch-product-service`, direct `localhost:7009`)

#### Get all products

`GET /products`

Response `200 OK`:
```json
[
  {"id": 1, "name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1500000.0},
  {"id": 2, "name": "Smartphone", "description": "Samsung Galaxy A54", "price": 850000.0}
]
```

#### Get product by id

`GET /products/{id}`, for example `GET /products/1`

Response `200 OK`:
```json
{"id": 1, "name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1500000.0}
```

Response `404 Not Found` with an empty body if the product does not exist.

#### Get products by name

`GET /products/name/{name}`, for example `GET /products/name/lap`. The match is case-insensitive and finds names that contain the text.

Response `200 OK`:
```json
[
  {"id": 1, "name": "Laptop", "description": "Dell Inspiron 15 laptop", "price": 1500000.0}
]
```

Response `404 Not Found` with an empty body if nothing matches.

### ProductInvetory (gateway `localhost:8080/inventory-service`, direct `localhost:7004`)

#### Add stock

`POST /inventory/add`

Request:
```json
{"productId": 1, "quantity": 50}
```

Response `200 OK`:
```json
{"id": 1, "productId": 1, "openingQuantity": 0, "quantity": 50, "closingQuantity": 50, "action": "ADD", "actionDate": "2026-10-29"}
```

#### Deduct stock

`POST /inventory/deduct`

Request:
```json
{"productId": 1, "quantity": 2}
```

Response `200 OK`:
```json
{"id": 2, "productId": 1, "openingQuantity": 50, "quantity": 2, "closingQuantity": 48, "action": "DEDUCT", "actionDate": "2026-10-29"}
```

Response `400 Bad Request`: `Insufficient quantity`

#### Get all inventory records with product name

`GET /inventory`

Response `200 OK`:
```json
[
  {"id": 1, "productId": 1, "productName": "Laptop", "openingQuantity": 0, "quantity": 50, "closingQuantity": 50, "action": "ADD", "actionDate": "2026-10-29"},
  {"id": 2, "productId": 1, "productName": "Laptop", "openingQuantity": 50, "quantity": 2, "closingQuantity": 48, "action": "DEDUCT", "actionDate": "2026-10-29"}
]
```

#### Get all product stock with product name

`GET /inventory/products`

Returns every product from FetchAllProduct with its current available quantity. Products with no stock show `0`.

Response `200 OK`:
```json
[
  {"productId": 1, "productName": "Laptop", "availableQuantity": 48},
  {"productId": 2, "productName": "Smartphone", "availableQuantity": 0}
]
```

#### Get available quantity

`GET /inventory/available/{productId}`, for example `GET /inventory/available/1`

Response `200 OK`:
```json
48
```

#### Get inventory history of one product

`GET /inventory/{productId}`, for example `GET /inventory/1`

Response `200 OK`:
```json
[
  {"id": 1, "productId": 1, "openingQuantity": 0, "quantity": 50, "closingQuantity": 50, "action": "ADD", "actionDate": "2026-10-29"},
  {"id": 2, "productId": 1, "openingQuantity": 50, "quantity": 2, "closingQuantity": 48, "action": "DEDUCT", "actionDate": "2026-10-29"}
]
```

#### Get today's inventory records of one product

`GET /inventory/{productId}/today`, for example `GET /inventory/1/today`

The response has the same shape as the history endpoint, limited to records with today's `actionDate`.

### OrderService (gateway `localhost:8080/order-service`, direct `localhost:8089`)

#### Place order

`POST /orders`

Request:
```json
{"productId": 1, "quantity": 2}
```

Response `201 Created`:
```json
{"id": 1, "quantity": 2, "totalPrice": 3000000.0, "status": "PLACED", "orderDate": "2026-10-29T10:15:30.123"}
```

Response `404 Not Found`: `Product not found`

Response `400 Bad Request`: `Insufficient quantity, available: 1`

Response `503 Service Unavailable`: `Product or Inventory service unavailable. Reason: ...` (rate limit, bulkhead full or circuit open)

#### Cancel order

`PUT /orders/{id}/cancel`, for example `PUT /orders/1/cancel`

Response `200 OK`:
```json
{"id": 1, "quantity": 2, "totalPrice": 3000000.0, "status": "CANCELLED", "orderDate": "2026-10-29T10:15:30.123"}
```

Response `404 Not Found`: `Order not found`

Response `400 Bad Request`: `Order already cancelled`

Response `503 Service Unavailable`: `Unable to cancel order right now. Service unavailable.`

#### Get all orders with order items and product name

`GET /orders`

Response `200 OK`:
```json
[
  {
    "id": 1,
    "quantity": 2,
    "totalPrice": 3000000.0,
    "status": "PLACED",
    "orderDate": "2026-10-29T10:15:30.123",
    "orderItems": [
      {"id": 1, "productId": 1, "productName": "Laptop", "quantity": 2, "price": 1500000.0, "totalAmount": 3000000.0}
    ]
  }
]
```

#### Get items of one order with product name

`GET /orders/{id}/items`, for example `GET /orders/1/items`

Response `200 OK`:
```json
[
  {"id": 1, "productId": 1, "productName": "Laptop", "quantity": 2, "price": 1500000.0, "totalAmount": 3000000.0}
]
```

## Running

1. Make sure MySQL is running. The databases are created automatically on first start.
2. `application.properties` is not tracked by git. Create it in each service's `src/main/resources` with the name and port from the table above.
3. Start Eureka-Server first, then api-gateway, AddUpdateProduct, FetchAllProduct, ProductInvetory and OrderService. Run each one with `./mvnw spring-boot:run` from its folder.
4. Start the second FetchAllProduct and OrderService instances on ports 7010 and 8090, as shown in Multiple instances.
5. Check `localhost:8761` to see that all services are registered, with two instances for fetch-product-service and order-service.
6. Add stock with `POST localhost:8080/inventory-service/inventory/add`, then place an order with `POST localhost:8080/order-service/orders`.

## Project Progress

We have completed a working microservices system with four business services for products, inventory and orders. Each service has its own database and talks to the others through Feign clients. Eureka handles service discovery, and the API gateway gives clients one entry point with request logging. OrderService is now protected with a circuit breaker, retry, rate limiter, bulkhead and time limiter, so it fails gracefully when other services are slow or down. All endpoints are documented with sample requests and responses for Postman testing.

## Lessons Learnt

1. Splitting a system into small services with their own databases makes each part easier to build, test and change without affecting the others.
2. Eureka and an API gateway remove hardcoded addresses, so services can move or scale without changing the code that calls them.
3. Resilience patterns such as circuit breaker, retry, rate limiting and bulkhead keep one failing or overloaded service from bringing down the whole system.

## Applicability to Our Institution

1. Our internal systems, such as stock, procurement and service requests, can be built as separate services that are deployed and updated independently.
2. A single gateway gives one secure, logged entry point to institutional services, which makes monitoring and auditing easier.
3. Resilience patterns keep critical public services available during peak usage or partial outages, which improves service delivery to citizens.
