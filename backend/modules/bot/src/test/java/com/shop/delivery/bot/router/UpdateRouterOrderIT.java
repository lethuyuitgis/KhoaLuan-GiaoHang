package com.shop.delivery.bot.router;

import com.shop.delivery.bot.handler.UpdateHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
    UpdateRouterOrderIT.TestConfig.class
})
class UpdateRouterOrderIT {

    static final List<String> CALL_ORDER = new ArrayList<>();

    @Autowired List<UpdateHandler> handlers;

    @Test
    void springSortsByOrderAnnotation() {
        // The injected List<UpdateHandler> must be ordered: first(0) → middle(50) → last(100)
        List<String> classNames = handlers.stream()
            .map(h -> h.getClass().getSimpleName())
            .toList();
        int idxFirst  = classNames.indexOf("FirstHandler");
        int idxMiddle = classNames.indexOf("MiddleHandler");
        int idxLast   = classNames.indexOf("LastHandler");

        assertThat(idxFirst).isLessThan(idxMiddle);
        assertThat(idxMiddle).isLessThan(idxLast);
    }

    // WARNING fix from plan-checker: `@SpringBootTest(classes = TestConfig.class)` requires a
    // @SpringBootConfiguration (or @SpringBootApplication) on the target — a bare @Configuration
    // is rejected with "Unable to find a @SpringBootConfiguration". Matches the pattern used by
    // ProcessedUpdateServiceIT in this module.
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
        org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class
    })
    static class TestConfig {
        @Bean @Order(0)   UpdateHandler firstHandler()  { return new FirstHandler();  }
        @Bean @Order(50)  UpdateHandler middleHandler() { return new MiddleHandler(); }
        @Bean @Order(100) UpdateHandler lastHandler()   { return new LastHandler();   }
    }

    static class FirstHandler  implements UpdateHandler {
        @Override public boolean canHandle(Update u) { return false; }
        @Override public void handle(Update u) {}
    }
    static class MiddleHandler implements UpdateHandler {
        @Override public boolean canHandle(Update u) { return false; }
        @Override public void handle(Update u) {}
    }
    static class LastHandler   implements UpdateHandler {
        @Override public boolean canHandle(Update u) { return false; }
        @Override public void handle(Update u) {}
    }
}
