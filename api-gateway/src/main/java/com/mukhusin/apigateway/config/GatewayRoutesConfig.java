package com.mukhusin.apigateway.config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import static org.springframework.web.servlet.function.RequestPredicates.path;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class GatewayRoutesConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutesConfig.class);

    @Bean
    public RouterFunction<ServerResponse> serviceRoutes() {
        return serviceRoute("order-service")
                .and(serviceRoute("inventory-service"))
                .and(serviceRoute("fetch-product-service"))
                .and(serviceRoute("add-update-product"));
    }

    // /{serviceName}/** -> strip first segment -> forward to serviceName via Eureka
    private RouterFunction<ServerResponse> serviceRoute(String serviceName) {
        return route(serviceName + "-route")
                .route(path("/" + serviceName + "/**"), http())
                .before(stripPrefix(1))
                .filter(lb(serviceName))
                .filter(loggingFilter())
                .build();
    }

    //log every request and response
    private HandlerFilterFunction<ServerResponse, ServerResponse> loggingFilter() {
        return (request, next) -> {
            long start = System.currentTimeMillis();
            log.info("Incoming: {} {}", request.method(), request.uri().getPath());

            ServerResponse response = next.handle(request);

            log.info("Outgoing: {} status={} time={} ms",
                    request.uri().getPath(),
                    response.statusCode(),
                    System.currentTimeMillis() - start);
            return response;
        };
    }
}