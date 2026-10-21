package com.codesupreme.sifarisqrupu.auth;

import com.codesupreme.sifarisqrupu.dao.user.UserRepository;
import com.codesupreme.sifarisqrupu.model.user.User;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

/** Parse the actual repository HQL against the entity/dialect without contacting a database. */
class MobileLoginQueryTest {
    @Test void accountAndDeviceQueriesCompileForMariaDb() throws Exception {
        var configuration = new Configuration().addAnnotatedClass(User.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory(); var session = factory.openSession()) {
            var phones = UserRepository.class.getMethod("findByPhoneDigits", List.class).getAnnotation(Query.class).value();
            session.createQuery(phones, User.class).setParameter("phones", List.of("0551234567"));
            var device = UserRepository.class.getMethod("isDeviceBlocked", String.class).getAnnotation(Query.class).value();
            session.createQuery(device, Boolean.class).setParameter("phoneId", "device");
        }
    }
}
