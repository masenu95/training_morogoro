# training_morogoro

Four Spring Boot services for managing products, inventory and orders, plus a Eureka server. Each service uses Spring Data JPA with MySQL and exposes a REST API that other services and Postman can call. Services register with Eureka and call each other by service name with OpenFeign clients (`@FeignClient`).

## Services

| Service | Eureka name | Port | Database | Responsibility |
|---|---|---|---|---|
| Eureka-Server | netflix-eureka-server | 8761 | - | Service registry |
| AddUpdateProduct | add-update-product | 7000 | training_products | Add and update products, and seed 12 sample products on startup |
| FetchAllProduct | fetch-product-service | 7001 | training_products | Fetch all products, a product by id, or products by name |
| ProductInvetory | inventory-service | 7004 | inventory_db | Record stock ADD/DEDUCT actions with opening and closing quantities |
| OrderService | order-service | 8089 | order_db | Place, cancel and list orders and order items |

AddUpdateProduct and FetchAllProduct share the `products` table in `training_products`. A product added on 7000 can be read on 7001.

## Communication

```
                          Eureka-Server :8761
                     (every service registers here)

AddUpdateProduct :7000 ──writes──▶ [training_products] ◀──reads── FetchAllProduct :7001
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

1. Postman sends `POST localhost:8089/orders` with `{"productId":1,"quantity":2}`.
2. OrderService gets the product from FetchAllProduct. If the product is not found, it returns `404`.
3. OrderService gets the available quantity from ProductInvetory. If the quantity is too low, it returns `400`.
4. OrderService asks ProductInvetory to deduct the quantity. ProductInvetory saves a DEDUCT record.
5. OrderService saves an `Order` with status `PLACED`, and an `OrderItem` with the price and total amount.

### Cancel order flow

1. Postman sends `PUT localhost:8089/orders/{id}/cancel`.
2. For each order item, OrderService asks ProductInvetory to add the quantity back. ProductInvetory saves an ADD record.
3. OrderService sets the order status to `CANCELLED`.

## API reference

Error responses are plain text, for example `Product not found`.

### AddUpdateProduct (localhost:7000)

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

### FetchAllProduct (localhost:7001)

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

### ProductInvetory (localhost:7004)

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

### OrderService (localhost:8089)

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

#### Cancel order

`PUT /orders/{id}/cancel`, for example `PUT /orders/1/cancel`

Response `200 OK`:
```json
{"id": 1, "quantity": 2, "totalPrice": 3000000.0, "status": "CANCELLED", "orderDate": "2026-10-29T10:15:30.123"}
```

Response `404 Not Found`: `Order not found`

Response `400 Bad Request`: `Order already cancelled`

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

#### Get items of one order

`GET /orders/{id}/items`, for example `GET /orders/1/items`

Response `200 OK`:
```json
[
  {"id": 1, "orderId": 1, "productId": 1, "quantity": 2, "price": 1500000.0, "totalAmount": 3000000.0}
]
```

## Running

1. Make sure MySQL is running with user `db_user`. The databases are created automatically on first start.
2. `application.properties` is not tracked by git. Create it in each service's `src/main/resources` with the name and port from the table above.
3. Start Eureka-Server first, then AddUpdateProduct, FetchAllProduct, ProductInvetory and OrderService. Run each one with `./mvnw spring-boot:run` from its folder.
4. Check `localhost:8761` to see that all four services are registered.
5. Add stock with `POST localhost:7004/inventory/add`, then place an order with `POST localhost:8089/orders`.
