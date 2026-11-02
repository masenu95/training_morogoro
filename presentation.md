# Order Management System with Microservices

Training – Morogoro

---

## 1. What We Built

A simple order management system split into small services:

- **Products**: add, update and view products
- **Inventory**: add and deduct stock
- **Orders**: place and cancel orders

Each service runs on its own and has its own database.

---

## 2. Why We Chose This

**Why an order management system?**

- Everyone understands it: products, stock and orders.
- It needs services to work together, for example an order needs the price and the stock.
- It is close to the real systems our institution uses.

**Why split it into services?**

| Reason | How it helps |
|---|---|
| Learning | Each service teaches one idea: JPA, Feign, Eureka, Gateway, Resilience4j, Zipkin |
| Reliability | If one service fails, the others keep running, and fallbacks give a clear message |
| Optimization | A busy service, like orders, can be scaled alone without scaling the whole system |

---

## 3. Technologies We Used

- **Spring Boot**: to build each service
- **Spring Data JPA + MySQL**: to save data
- **REST API**: so other services and Postman can call each service
- **OpenFeign**: for services to talk to each other
- **Eureka**: so services can find each other by name
- **API Gateway**: one entry point for all requests
- **Resilience4j**: to protect the system when something fails
- **Zipkin**: to trace each request as it moves across services

---

## 4. Our Services

| Service | What it does |
|---|---|
| Eureka Server | Keeps a list of all running services |
| API Gateway | Receives all requests and sends them to the right service |
| AddUpdateProduct | Adds and updates products |
| FetchAllProduct (x2) | Lists products, and finds them by id or name |
| ProductInventory | Records stock ADD/DEDUCT with opening and closing quantity |
| OrderService (x2) | Places and cancels orders, and saves order items |

---

## 5. Architecture

```
                         Postman / Client
                                │
                                ▼
                     API Gateway :8080  (logging)
                                │
      ┌──────────────┬──────────┴──────┬────────────────┐
      ▼              ▼                 ▼                ▼
 AddUpdateProduct  FetchAllProduct  OrderService    ProductInventory
     :7002        :7009, :7010     :8089, :8090         :7004
      │              │             │       │  Feign       │
      │              │◀── Feign ───┘       └─────────────▶│
      ▼              ▼                 ▼                ▼
   [training_products DB]          [order_db]      [inventory_db]


  Eureka Server :8761  ◀── all services register and find each other here
  Zipkin        :9411  ◀── all services send traces here
```

- **API Gateway**: the single entry point. It finds services through Eureka and logs every request.
- **Eureka**: every service registers by name, so no fixed addresses are needed.
- **Feign**: OrderService calls the product and inventory services, and the inventory service calls the product service.
- **Zipkin**: every service reports how long each step of a request took.

---

## 6. Patterns We Used

| Group | Patterns |
|---|---|
| Design | Service per business area, database per service, separate read and write product services |
| Communication | API Gateway, service discovery (Eureka), REST with Feign, load balancing, multiple instances |
| Reliability | Circuit breaker with fallback, retry, rate limiter, bulkhead, time limiter |
| Observability | Tracing (Zipkin), request logging to a file, health checks (Actuator) |

**Next to add:** Saga for orders, and a config server.

---

## 7. Placing an Order

1. The customer sends an order with a product and quantity.
2. OrderService gets the product price from the product service.
3. OrderService checks the stock in the inventory service.
4. If there is enough stock, the inventory is deducted.
5. The order and its order items are saved.

**Cancelling an order** returns the quantity back to inventory.

---

## 8. Inventory Tracking

Every stock movement is recorded:

| Product | Opening | Quantity | Closing | Action |
|---|---|---|---|---|
| Laptop | 0 | 50 | 50 | ADD |
| Laptop | 50 | 2 | 48 | DEDUCT |

The closing quantity becomes the next opening quantity.

---

## 9. API Gateway and Logging

- All requests go through one address: `localhost:8080`
- Example: `localhost:8080/order-service/orders`
- The gateway logs every request: what came in, the status, and how long it took
- The logs are saved to a file, `logs/api-gateway.log`, so we can check them later
- A new file starts every 10 MB, and files older than 7 days are deleted

---

## 10. Protecting the System (Resilience)

| Pattern | Simple meaning |
|---|---|
| Circuit Breaker | Stop calling a service that keeps failing, then try again later |
| Retry | Try again if a call fails |
| Rate Limiter | Allow only 5 orders per second |
| Bulkhead | Allow only 3 orders processing at the same time |
| Time Limiter | Stop waiting if a call takes too long |

When protection is triggered, the user gets a clear message instead of a crash.

---

## 11. Multiple Instances

FetchAllProduct and OrderService get the most traffic, so we run two copies of each.

| Service | Instance 1 | Instance 2 |
|---|---|---|
| fetch-product-service | 7009 | 7010 |
| order-service | 8089 | 8090 |

- Both copies register in Eureka with the same name.
- The gateway and Feign share requests between the copies.
- If one copy stops, the other keeps serving requests.

---

## 12. Tracing with Zipkin

Every request gets a **Trace ID**, and each service it passes through is recorded as a **span**.

Example: placing an order (`POST /orders`)

| Step | Service | Time |
|---|---|---|
| Place order | order-service | 124 ms (total) |
| Get product price | fetch-product-service `GET /products/{id}` | 32 ms |
| Check stock | inventory-service `GET /inventory/available/{productId}` | 8 ms |
| Deduct stock | inventory-service `POST /inventory/deduct` | 31 ms |

One request, 3 services, 7 spans, all shown on one screen at `localhost:9411`.

**Why it helps:**

- We can see the full path of a request across services.
- We can find which service is slow.
- We can find where an error happened.

---

## 13. Lessons Learnt

1. Small services are easier to build, test and change.
2. Eureka and the gateway remove fixed addresses, so services can move or scale easily.
3. Resilience patterns and Zipkin tracing help us keep the system running and quickly find where problems are.

---

## 14. How This Helps Our Institution

1. Our systems and service requests, can be built as separate services.
2. One gateway with logging, plus Zipkin tracing, makes monitoring and troubleshooting easier.
3. Our services stay available even during high traffic or partial failures.

---

## Thank You

Questions?
