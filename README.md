# training_morogoro

Four Spring Boot services for managing products, inventory and orders, plus a Eureka server and an API gateway. Each service uses Spring Data JPA with MySQL and exposes a REST API. Services share data through events on RabbitMQ and keep local copies of the data they need. They register with Eureka, and OrderService uses OpenFeign clients (`@FeignClient`) as a fallback when its local copy is not enough. Clients such as Postman go through the gateway, which logs every request. OrderService is protected with Resilience4j: circuit breaker, retry, rate limiter, bulkhead and time limiter.

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

The logs are shown in the console and also saved to `api-gateway/logs/api-gateway.log`. The path is absolute, so the file goes there no matter which folder the gateway is started from. A new file starts every 10 MB, and files older than 7 days are deleted:

```
logging.file.name=/home/mukhusin-siraji/Dev/training/training_morogoro/api-gateway/logs/api-gateway.log
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

Services share data in two ways:

- **Events through RabbitMQ (main way):** when a service changes its data, it publishes an event. Other services listen and keep a local copy of the data they need, so they do not call each other to read data.
- **Feign through Eureka (fallback):** OrderService calls another service only when its local copy is missing or not enough to be sure before accepting an order.

```
                         Postman ──▶ api-gateway :8080 ──▶ any service (via Eureka)

                                    RabbitMQ :5672
   ┌──────────────────────────────────────────────────────────────────────────┐
   │                                                                          │
   │  AddUpdateProduct ──publish──▶ [product-events] ──▶ ProductInvetory      │
   │                                                └──▶ OrderService         │
   │                                                                          │
   │  ProductInvetory  ──publish──▶ [inventory-events] ──▶ OrderService       │
   │                                                                          │
   │  OrderService     ──publish──▶ [order-events] ──▶ ProductInvetory        │
   │                                                                          │
   │  OrderService     ──publish──▶ [createOrders] ──▶ OrderService (saves)   │
   │                                                                          │
   └──────────────────────────────────────────────────────────────────────────┘

   OrderService ──Feign (fallback only)──▶ FetchAllProduct  GET /products/{id}
   OrderService ──Feign (fallback only)──▶ ProductInvetory  GET /inventory/available/{productId}
```

### Events

| Exchange | Published by | When | Event | Consumed by (queue) | What the consumer does |
|---|---|---|---|---|---|
| `product-events` | AddUpdateProduct | Product added, updated, and all products on startup | `ProductEvent(id, name, price, action)` with action `ADDED`, `UPDATED` or `SYNC` | ProductInvetory (`product-events.inventory-service`) | Saves the product in its `product_info` table |
| | | | | OrderService (`product-events.order-service`) | Saves the product in its `product_info` table |
| `inventory-events` | ProductInvetory | Every ADD or DEDUCT, and current stock on startup | `InventoryEvent(productId, quantity, closingQuantity, action)` with action `ADD`, `DEDUCT` or `SYNC` | OrderService (`inventory-events.order-service`) | Saves the closing quantity in its `stock_info` table |
| `order-events` | OrderService | Order saved or cancelled | `OrderEvent(orderId, productId, quantity, totalPrice, status)` with status `PLACED` or `CANCELLED` | ProductInvetory (`order-events.inventory-service`) | `PLACED` records a DEDUCT, `CANCELLED` records an ADD |
| `createOrders` | OrderService | Order request accepted | `OrderMessage(productId, quantity, price)` | OrderService (`createOrders.createOrder-group`) | Saves the `Order` and `OrderItem` |

FetchAllProduct does not publish or consume events. It shares the `training_products` database with AddUpdateProduct, so it sees new products straight away.

### Local copies

| Service | Table | Filled from | Used for |
|---|---|---|---|
| ProductInvetory | `product_info` (id, name, price) | `product-events` | Product names in `GET /inventory` and `GET /inventory/products` |
| OrderService | `product_info` (id, name, price) | `product-events` | Product price when placing an order, product names in `GET /orders` |
| OrderService | `stock_info` (productId, availableQuantity) | `inventory-events` | Stock check when placing an order |

The tables use the id from the event, so `save()` inserts the row the first time and updates it after that. Receiving the same event twice does not create duplicates.

RabbitMQ only delivers messages and removes them once they are read, so the tables keep the latest data for later requests. Both OrderService instances share `order_db`, so whichever instance receives an event, both see the update.

### Feign fallback

| Caller | Called service | Endpoint | When |
|---|---|---|---|
| OrderService | fetch-product-service | `GET /products/{id}` | The product is not in the local `product_info` |
| OrderService | inventory-service | `GET /inventory/available/{productId}` | The product is not in the local `stock_info`, or the local quantity is lower than the order quantity |

The answer is saved in the local table. If the called service is down, the circuit breaker fallback returns `503`, so an order is never accepted while the stock is unknown.

### Add or update product flow

1. Postman sends `POST` or `PUT localhost:8080/add-update-product/products`.
2. AddUpdateProduct saves the product and publishes a `ProductEvent` to `product-events`.
3. ProductInvetory and OrderService each save the product in their `product_info` table.

### Add stock flow

1. Postman sends `POST localhost:8080/inventory-service/inventory/add`.
2. ProductInvetory saves an ADD record and publishes an `InventoryEvent` with the new closing quantity.
3. OrderService saves the closing quantity in `stock_info`.

### Place order flow

1. Postman sends `POST localhost:8080/order-service/orders` with `{"productId":1,"quantity":2}`.
2. OrderService reads the price from its `product_info`. If the product is missing, it asks FetchAllProduct with Feign. If the product does not exist, it returns `404`.
3. OrderService reads the stock from its `stock_info`. If it is missing or too low, it asks ProductInvetory with Feign. If the stock is still too low, it returns `400`.
4. OrderService publishes an `OrderMessage` to `createOrders`, lowers its local `stock_info`, and returns `202 Accepted`.
5. OrderService consumes the `OrderMessage`, saves the `Order` with status `PLACED` and its `OrderItem`, then publishes an `OrderEvent` (`PLACED`) to `order-events`.
6. ProductInvetory consumes the `OrderEvent` and records a DEDUCT, then publishes an `InventoryEvent`.
7. OrderService consumes the `InventoryEvent` and updates `stock_info` with the real closing quantity.

If there are too many requests, too many orders at once, or the circuit is open, OrderService returns `503` from its fallback.

### Cancel order flow

1. Postman sends `PUT localhost:8080/order-service/orders/{id}/cancel`.
2. OrderService sets the order status to `CANCELLED` and publishes an `OrderEvent` (`CANCELLED`) for each order item.
3. ProductInvetory consumes the event and records an ADD, then publishes an `InventoryEvent`.
4. OrderService consumes the `InventoryEvent` and updates `stock_info`.

If ProductInvetory is down, the events wait in the `order-events.inventory-service` queue and the stock is returned when it starts again.

### Startup sync

- AddUpdateProduct publishes every product with action `SYNC` when it starts.
- ProductInvetory publishes the current stock of every product with action `SYNC` when it starts.
- Producers declare the consumer queues with `producer.required-groups`, so events wait in the queue even if the consumer has not started yet.

### RabbitMQ settings

All services connect to RabbitMQ on `localhost:5672` with `guest` / `guest`. The bindings are in each service's `application.properties`:

AddUpdateProduct:
```
spring.cloud.stream.bindings.productEvents-out-0.destination=product-events
spring.cloud.stream.bindings.productEvents-out-0.producer.required-groups=inventory-service,order-service
```

ProductInvetory:
```
spring.cloud.function.definition=productEvents;orderEvents
spring.cloud.stream.bindings.productEvents-in-0.destination=product-events
spring.cloud.stream.bindings.productEvents-in-0.group=inventory-service
spring.cloud.stream.bindings.orderEvents-in-0.destination=order-events
spring.cloud.stream.bindings.orderEvents-in-0.group=inventory-service
spring.cloud.stream.bindings.inventoryEvents-out-0.destination=inventory-events
spring.cloud.stream.bindings.inventoryEvents-out-0.producer.required-groups=order-service
```

OrderService:
```
spring.cloud.function.definition=receiveOrders;productEvents;inventoryEvents
spring.cloud.stream.bindings.sendOrder-out-0.destination=createOrders
spring.cloud.stream.bindings.receiveOrders-in-0.destination=createOrders
spring.cloud.stream.bindings.receiveOrders-in-0.group=createOrder-group
spring.cloud.stream.bindings.productEvents-in-0.destination=product-events
spring.cloud.stream.bindings.productEvents-in-0.group=order-service
spring.cloud.stream.bindings.inventoryEvents-in-0.destination=inventory-events
spring.cloud.stream.bindings.inventoryEvents-in-0.group=order-service
spring.cloud.stream.bindings.orderEvents-out-0.destination=order-events
spring.cloud.stream.bindings.orderEvents-out-0.producer.required-groups=inventory-service
```

Open `localhost:15672` (`guest` / `guest`) to see the exchanges, the queues and the message rates.

## Patterns used

| Group | Pattern | Where |
|---|---|---|
| Design | Decompose by business capability | Products, inventory and orders are separate services |
| Design | Database per service | order_db, inventory_db, training_products |
| Design | Command/query split | AddUpdateProduct writes, FetchAllProduct reads |
| Communication | API Gateway | Single entry point on 8080 with routing and logging |
| Communication | Service registry and discovery | Eureka, services call each other by name |
| Communication | Event-driven communication | RabbitMQ with Spring Cloud Stream: `product-events`, `inventory-events`, `order-events` |
| Communication | Local read model (data replication) | `product_info` and `stock_info` tables filled from events |
| Communication | Asynchronous request processing | Order requests go through `createOrders` and are saved by the consumer |
| Communication | Synchronous REST with a declarative client | OpenFeign, used as a fallback |
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
- Two orders can pass the local stock check before inventory catches up. If inventory then has too little stock, it logs a warning and skips the deduction, but the order stays `PLACED`. A Saga with an "order rejected" event would fix this.
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

Response `202 Accepted`: `Order received and will be saved shortly`

The order is saved a moment later by the `createOrders` consumer. Use `GET /orders` to see it.

Response `404 Not Found`: `Product not found`

Response `400 Bad Request`: `Insufficient quantity, available: 1`

Response `503 Service Unavailable`: `Product or Inventory service unavailable. Reason: ...` (rate limit, bulkhead full, circuit open, or the Feign fallback could not reach the service). `Order not received, please try again` if the order request could not be sent to RabbitMQ.

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
2. Make sure RabbitMQ is running, for example `docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management`.
3. `application.properties` is not tracked by git. Create it in each service's `src/main/resources` with the name and port from the table above.
4. Start Eureka-Server first, then api-gateway, AddUpdateProduct, FetchAllProduct, ProductInvetory and OrderService. AddUpdateProduct and ProductInvetory publish their data on startup, so OrderService gets the copies even if it starts later. Run each one with `./mvnw spring-boot:run` from its folder.
5. Start the second FetchAllProduct and OrderService instances on ports 7010 and 8090, as shown in Multiple instances.
6. Check `localhost:8761` to see that all services are registered, with two instances for fetch-product-service and order-service.
7. Add stock with `POST localhost:8080/inventory-service/inventory/add`, then place an order with `POST localhost:8080/order-service/orders`.

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
