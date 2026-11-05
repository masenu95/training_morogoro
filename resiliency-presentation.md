# Resiliency in Microservices

Case Study: Our Order Management System · Spring Boot + Resilience4j

---

## 1. How This Case Study Works

1. **Background:** what resiliency is and why microservices need it
2. **The System:** our order management system and its services
3. **The Challenge:** what goes wrong without resiliency
4. **The Solution:** five patterns plus an event-driven design
5. **Results:** what we measured after breaking it on purpose
6. **Live Demo:** see each pattern work in the real system

**Why a case study?** We built a real system with four business services, then broke it on purpose: traffic spikes, a slow service, a failing service and a stopped service. We compared what happened before and after adding resiliency, and measured the results.

---

## 2. Why Resiliency?

In microservices, services depend on each other. Problems will happen:

- A service is **down** or **slow**
- **Too many requests** arrive at the same time
- One failure **spreads** and brings down other services

**Resiliency** means the system keeps working, fails fast with a clear message, and recovers on its own.

We used **Resilience4j** with Spring Boot.

---

## 3. Key Terms

| Pattern | What it means | Example in our system |
|---|---|---|
| Rate Limiter | Limits how many requests are allowed in a period of time | 5 orders per second |
| Bulkhead | Limits how many requests run at the same time, so one busy part cannot use all resources | 3 orders processed at once |
| Circuit Breaker | Stops calling a service that keeps failing, then tries again after a wait | Opens when 50% of the last 10 calls fail, waits 10 s |
| Retry | Tries a failed call again automatically, for short temporary errors | Cancel order: 3 attempts |
| Time Limiter | Sets the maximum time to wait for a call, then stops and returns the fallback | 3 seconds on every Feign call |
| Fallback | The backup answer the user gets when a protection is triggered | Clear `503` message |

---

## 4. The System: Order Management

```
Users ──▶ API Gateway ──▶ AddUpdateProduct     (adds and updates products)
                     ├──▶ FetchAllProduct ×2   (lists and searches products)
                     ├──▶ ProductInventory     (stock in and stock out)
                     └──▶ OrderService ×2      (places and cancels orders)

Supporting infrastructure: Eureka (finds services), Config Server (shared settings),
RabbitMQ (events between services), MySQL (one database per service)
```

**Order flow:** user → gateway → OrderService checks product and stock → order message → RabbitMQ → order saved → events update inventory.

---

## 5. The Challenge: Before Resiliency

```
Users ──1000 orders──▶ API Gateway ──▶ OrderService ──▶ FetchAllProduct
                                            │
                                            └──waits 30 s──▶ ProductInventory (slow or down) ✖
```

- **Overload:** too many orders hit OrderService and the database at the same time.
- **Long waits:** a slow ProductInventory makes every order wait with no limit.
- **Failure spreads:** threads are blocked, OrderService stops answering, and users get timeouts and error pages.

---

## 6. Our Solution: After Resiliency

```
                         ┌──────────── OrderService ────────────┐
Users ──▶ API Gateway ──▶│ Rate Limiter 5/s   Bulkhead 3        │──▶ FetchAllProduct (Rate Limiter 3/s)
  ▲                      │ Circuit Breaker    Time Limiter 3 s  │──3 s max──▶ ProductInventory (slow or down)
  │                      │ Retry 3x (cancel)  Fallback 503      │──▶ RabbitMQ (events wait in the queue)
  │                      │ Local copy: product_info, stock_info │
  │                      └──────────────────────────────────────┘
  └── 202 Accepted, or a fast 503 with a clear message
```

- **Controlled traffic:** rate limiter and bulkhead stop floods before they reach the database.
- **Fast failure:** time limiter and circuit breaker answer in 3 s, not 30 s, with a clear 503.
- **Keeps working:** local copies and queues let orders continue while ProductInventory is down.

| Service | Protection | Setting |
|---|---|---|
| OrderService (place order) | Rate Limiter | 5 orders per second |
| OrderService (place order) | Bulkhead | 3 orders at the same time |
| OrderService (place, cancel) | Circuit Breaker | Opens at 50% failures of last 10 calls, waits 10 s |
| OrderService (cancel) | Retry | 3 attempts, 10 s apart |
| OrderService (Feign calls) | Time Limiter | 3 seconds |
| FetchAllProduct (list products) | Rate Limiter | 3 requests per second |
| All services | Events + local copies | Keep working when another service is down |

---

## 7. The Journey of an Order

```
Request → Rate Limiter → Bulkhead → Circuit Breaker → placeOrder() → Feign (Time Limiter 3 s) → Inventory
              │             │              │                               │
         too many      all slots       circuit OPEN                  slower than 3 s
              └─────────────┴──────────────┴──────▶ Fallback 503 ◀─────────┘
```

All checks pass: the order is sent to RabbitMQ, local stock is reduced, and the user gets 202 Accepted.

---

## 8. Rate Limiter and Bulkhead

**Rate Limiter:** limits how many requests are allowed in a time window.

- OrderService: 5 orders per second, extra requests get `503` immediately.
- FetchAllProduct: 3 product lists per second, extra requests get `429 Too Many Requests`.
- Why: protects the service and the database from traffic spikes.

**Bulkhead:** limits how many requests run at the same time, like the compartments of a ship.

- Only 3 orders are processed at once. A 4th waits 20 ms, then gets `503`.
- Why: a flood of orders cannot use all the threads, so other endpoints keep working.

```java
@RateLimiter(name = "orderRateLimiter")
@Bulkhead(name = "orderBulkhead", type = Bulkhead.Type.SEMAPHORE)
```

---

## 9. Rate Limiter and Bulkhead in Pictures

**Rate Limiter (5 per second):** 8 orders arrive in one second. The gate lets 1–5 through (202) and rejects 6–8 at once (503). The next second the gate refills.

**Bulkhead (3 at once):** OrderService has 3 slots, all busy. Order 4 waits 20 ms, then gets 503. Like walls in a ship, one flood cannot sink everything, so GET /orders and cancel keep working.

---

## 10. Rate Limiter: Before and After

| Without Rate Limiter | With Rate Limiter |
|---|---|
| Every request goes straight in | Only 5 orders per second pass the gate |
| Service and database overload | Extra requests get a fast 503 |
| Everyone gets slow answers or errors | Service and database stay healthy |

---

## 11. Bulkhead: Before and After

| Without Bulkhead | With Bulkhead |
|---|---|
| An order flood takes every thread | Orders limited to 3 at the same time |
| Other endpoints wait too | Free threads serve other endpoints |
| One busy feature freezes the whole service | A 4th order waits 20 ms, then gets 503 |

---

## 12. Circuit Breaker

**Stops calling a service that keeps failing.**

| State | Meaning |
|---|---|
| CLOSED | Normal, calls go through |
| OPEN | 50% of the last 10 calls failed, calls are blocked for 10 seconds |
| HALF_OPEN | After 10 seconds, 3 test calls are allowed. Success closes it again |

**Why:** users get a fast answer instead of waiting, and the failing service gets time to recover.

```java
@CircuitBreaker(name = "orderService", fallbackMethod = "placeOrderFallback")
```

---

## 13. Circuit Breaker in Action

```
✓ ✓ ✓ ✓ ✓ ✗ ✗ ✗ ✗ ✗ | ⊘ ⊘ | wait 10 s | ✓ ✓ ✓
      CLOSED          |   OPEN           | HALF_OPEN → CLOSED
```

Console: `CLOSED to OPEN`, `OPEN to HALF_OPEN`, `HALF_OPEN to CLOSED`.

---

## 14. Circuit Breaker: Before and After

| Without Circuit Breaker | With Circuit Breaker |
|---|---|
| Keeps calling a broken service | Stops calling after 50% of 10 calls fail |
| Every user waits for the same error | Users get a fast 503 while it is OPEN |
| The broken service gets no time to recover | Tests again after 10 s and closes by itself |

---

## 15. Retry and Time Limiter

**Retry:** try again when a call fails for a short moment.

- Cancel order: up to 3 attempts, 10 seconds apart, then fallback.

**Time Limiter:** stop waiting when a call takes too long.

- Every Feign call from OrderService to another service stops after 3 seconds.
- Turned on with `spring.cloud.openfeign.circuitbreaker.enabled=true` and `resilience4j.timelimiter.configs.default.timeout-duration=3s`.

**Fallback:** when any protection is triggered, the user gets a clear `503` message instead of an error page.

---

## 16. Time Limiter and Retry in Pictures

**Time Limiter:** OrderService asks inventory at 0 s. Inventory needs 5 s. At 3 s OrderService stops waiting and returns the fallback 503. The late answer at 5 s is ignored.

**Retry (cancel, 3 attempts, 10 s apart):**
- Short problem: ✗ → ✗ → ✓, order cancelled (200).
- Long problem: ✗ → ✗ → ✗, fallback "Unable to cancel order right now."

---

## 17. Time Limiter: Before and After

| Without Time Limiter | With Time Limiter |
|---|---|
| Waits as long as the other service takes (30 s) | Waits at most 3 seconds |
| Threads stay blocked | Returns a clear 503 at once |
| Users stare at a spinner | Threads are free again quickly |

---

## 18. Retry: Before and After

| Without Retry | With Retry |
|---|---|
| One short glitch means a failed request | Tries up to 3 times, 10 s apart |
| The user must try again by hand | Short glitches are fixed automatically |
| Small problems become visible errors | Still failing? A clear fallback message |

---

## 19. Resilient by Design

Resilience is not only annotations. Our architecture also protects us:

- **Local copies:** OrderService keeps its own product and stock data from events, so it can accept orders even when FetchAllProduct or ProductInventory is down.
- **Queues:** if ProductInventory is down, order events wait in RabbitMQ and stock is updated when it starts again.
- **Safety check:** if the local data is missing, OrderService confirms with Feign. If that fails, it refuses the order instead of guessing.
- **Multiple instances:** FetchAllProduct and OrderService run two copies each.

---

## 20. Events Keep Orders Flowing

```
AddUpdateProduct ──▶ product-events ───▶ OrderService (local copy) + ProductInventory
ProductInventory ──▶ inventory-events ─▶ OrderService (stock_info)
OrderService ──────▶ order-events ─────▶ ProductInventory (down: messages wait in the queue)
```

1. Inventory stops: OrderService still knows the stock from its local copy.
2. Orders keep coming: users get 202, and order events wait in RabbitMQ.
3. Inventory returns: it reads the waiting events, and its stock is correct again.

---

## 21. Results: What We Measured

| Scenario we tested | Without resiliency | With resiliency (measured) |
|---|---|---|
| Traffic spike: 8 orders in 1 s | All 8 hit OrderService and the database | 5 accepted in about 20 ms, 3 rejected in about 4 ms |
| ProductInventory slow (5 s) | Every order waits 5 s or more | Stops after 3.0 s with a clear 503 |
| ProductInventory failing | Every call fails slowly, again and again | Circuit OPEN after 10 calls, fast 503, recovers by itself |
| ProductInventory stopped | Orders cannot be placed | Orders still accepted (202), stock fixed when it returns |
| High traffic on products and orders | One instance carries all the load | Two instances each of FetchAllProduct and OrderService |

Measured on our system with the inventory simulate switch, curl and the circuit breaker logs.

---

## 22. Case Study Conclusions

- **Protect in layers:** rate limiter at the door, bulkhead for threads, circuit breaker and time limiter for calls, retry for short glitches.
- **Design matters as much as code:** events and local copies kept orders working while ProductInventory was down.
- **Fail fast and clearly:** a 503 with a reason in milliseconds is better than a 30 second wait or an error page.
- **Small change, big effect:** a few Resilience4j annotations and properties made the whole system safer.

---

## 23. Live Demo

1. **Normal order:** order is accepted (`202`)
2. **Rate Limiter:** send 8 orders quickly, extra ones are rejected
3. **Time Limiter:** make inventory slow, the order stops after 3 seconds
4. **Circuit Breaker:** make inventory fail, watch the circuit open, then recover
5. **Resilient by design:** stop ProductInventory, orders still work, stock updates when it returns

---

## 24. Thank You

Questions?

---

# Demo Guide (presenter notes, not slides)

### Before the presentation

1. Start MySQL and RabbitMQ (Docker Desktop, `rabbitmq` container).
2. Start in this order: Eureka-Server, ConfigServer, AddUpdateProduct, FetchAllProduct, ProductInvetory, OrderService. Run **one** OrderService instance so the limits are easy to see.
3. Add stock, so OrderService gets the stock through events:
   ```
   curl -X POST localhost:7004/inventory/add -H 'Content-Type: application/json' -d '{"productId":1,"quantity":100}'
   ```
4. Open these in the browser:
   - `localhost:8089/just-me/circuitbreakers` (circuit breaker state)
   - `localhost:15672` (RabbitMQ, guest / guest)

### Demo 1: Normal order

```
curl -X POST localhost:8089/orders -H 'Content-Type: application/json' -d '{"productId":1,"quantity":1}'
```

Expected: `Order received and will be saved shortly` (`202`).

### Demo 2: Rate Limiter

OrderService (5 per second):
```
for i in {1..8}; do curl -s -X POST localhost:8089/orders -H 'Content-Type: application/json' -d '{"productId":1,"quantity":1}'; echo; done
```

Expected: the first calls are accepted, then:
`Product or Inventory service unavailable. Reason: RateLimiter 'orderRateLimiter' does not permit further calls`

FetchAllProduct (3 per second):
```
for i in {1..6}; do curl -s -o /dev/null -w "%{http_code}\n" localhost:7009/products; done
```

Expected: `200 200 200 429 429 429`.

The accepted orders are real orders of quantity 1. Cancel them after the demo if needed.

### Inventory "break me" switch

ProductInvetory has a switch that changes how `GET /inventory/available/{productId}` behaves:

```
curl -X POST localhost:7004/inventory/simulate/slow    # waits 5 seconds
curl -X POST localhost:7004/inventory/simulate/error   # throws an error
curl -X POST localhost:7004/inventory/simulate/ok      # normal
```

OrderService only calls inventory when its local stock is lower than the order, so the demos order a very big quantity:

```
curl -s -X POST localhost:8089/orders -H 'Content-Type: application/json' -d '{"productId":1,"quantity":100000}'; echo
```

In `ok` mode this returns `Insufficient quantity, available: ...` (`400`), a normal answer. No order is created in any of these demos.

### Demo 3: Time Limiter

1. `curl -X POST localhost:7004/inventory/simulate/slow`
2. Send the big order. It fails after **3 seconds**, not 5:
   `Reason: java.util.concurrent.TimeoutException: TimeLimiter 'InventoryFeignClientgetAvailableQuantityLong' recorded a timeout exception.`

### Demo 4: Circuit Breaker

1. `curl -X POST localhost:7004/inventory/simulate/error`
2. Send the big order once per second:
   ```
   for i in {1..12}; do curl -s -X POST localhost:8089/orders -H 'Content-Type: application/json' -d '{"productId":1,"quantity":100000}'; echo; sleep 1; done
   ```
3. The first calls show the inventory error. Once half of the last 10 calls failed:
   `Reason: CircuitBreaker 'orderService' is OPEN and does not permit further calls`
   The OrderService console shows `>>> CIRCUIT BREAKER [orderService]: State transition from CLOSED to OPEN`, and the inventory console shows no more calls.
4. `curl -X POST localhost:7004/inventory/simulate/ok`, then wait 10 seconds. The console shows `OPEN to HALF_OPEN`.
5. Send the big order 3 times, slowly. The answer is `Insufficient quantity` again and the console shows `HALF_OPEN to CLOSED`.

On a freshly started OrderService, the circuit needs 10 calls before it can open. The time limiter demo counts as one of them.

### Demo 5: Resilient by design

1. Stop **ProductInvetory** in the IDE.
2. Place an order for product 1:
   ```
   curl -X POST localhost:8089/orders -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'
   ```
   Expected: still `202`. OrderService uses its local stock copy.
3. In RabbitMQ (`localhost:15672` > Queues), `order-events.inventory-service` has 1 message waiting.
4. Start ProductInvetory again. The message is consumed and stock is deducted:
   ```
   curl localhost:7004/inventory/1
   ```
   The last record is a `DEDUCT` of 2.

### Explain only (hard to show live)

- **Bulkhead:** orders finish in a few milliseconds, so 3 rarely overlap on one laptop.
- **Retry:** cancel only fails if the database is down.
