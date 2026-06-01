package com.shop.delivery.delivery.api;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal Boot config for {@code @WebMvcTest} slices in the delivery module.
 *
 * <p>Placed at {@code com.shop.delivery.delivery.api} so its package becomes the
 * ComponentScan root for the WebMvc slice — controllers under {@code api.customer}
 * and {@code api.admin} are discoverable.
 *
 * <p>Excludes JPA / datasource autoconfiguration so the controller slice doesn't try
 * to wire repositories. Tests must explicitly {@code @Import} non-controller beans
 * they depend on (e.g. {@code CurrentUserArgumentResolver}).
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
public class DeliveryWebMvcTestConfig {
}
