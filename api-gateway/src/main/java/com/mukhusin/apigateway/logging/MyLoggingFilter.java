//package com.mukhusin.apigateway.logging;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.function.RouterFunction;
//import org.springframework.web.servlet.function.ServerResponse;
//
//import static org.springframework.web.servlet.function.RequestPredicates.path;
//import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.stripPrefix;
//import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
//import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
//import static org.springframework.web.servlet.function.RequestPredicates.path;
//import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
//
//@Configuration
//public class MyLoggingFilter {
//    private static final Logger log = LoggerFactory.getLogger(MyLoggingFilter.class);
//
//    @Bean
//    public RouterFunction<ServerResponse> orderRoute() {
//        return route("orders-route")
//                // 1. Pass an EMPTY http() handler function
//                .route(path("/**"), http())
//                // 2. Supply the routing URL via a .before(uri(...)) filter
//                .before(uri("http://localhost:8089"))
//                // 3. Your logging filter chain
//                .filter((request, next) -> {
//                    long startTime = System.currentTimeMillis();
//                    log.info("=========================================");
//                    log.info("Incoming Request: {} {}", request.method(), request.uri().getPath());
//                    log.info("Headers: {}", request.headers().asHttpHeaders());
//                    log.info("=========================================");
//
//                    ServerResponse response = next.handle(request);
//
//                    long duration = System.currentTimeMillis() - startTime;
//                    log.info("=========================================");
//                    log.info("Outgoing Response for: {}", request.uri().getPath());
//                    log.info("HTTP Status: {}", response.statusCode());
//                    log.info("Execution Time: {} ms", duration);
//                    log.info("=========================================");
//
//                    return response;
//                })
//                .build();
//    }
//}
