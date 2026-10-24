package com.codesupreme.sifarisqrupu.push;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class DatabasePoolConfigurationTest {
    @Test void productionPoolRetiresAndPingsConnectionsBeforeMariaDbIdleTimeout() throws Exception {
        var properties=new Properties();
        try(var input=getClass().getResourceAsStream("/application.properties")) {properties.load(input);}
        var environment=new StandardEnvironment();
        environment.getPropertySources().addFirst(new PropertiesPropertySource("production-pool",properties));
        var pool=Binder.get(environment).bind("spring.datasource.hikari",HikariConfig.class).get();
        // Hikari rejects lifetimes under 30s, so MariaDB's old 10s wait_timeout
        // cannot be solved just by shortening the application's connection age.
        assertTrue(pool.getMaxLifetime()>=30000);
        assertTrue(pool.getMaxLifetime()<600000);
        assertTrue(pool.getKeepaliveTime()>=30000);
        assertTrue(pool.getKeepaliveTime()<pool.getMaxLifetime());
        assertTrue(pool.getValidationTimeout()<pool.getConnectionTimeout());
    }
}
