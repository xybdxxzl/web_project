package com.minioj.config;

import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;

class H2SchemaTest {

    @Test
    void addsProgressColumnsToExistingSubmissionsAndCanRunAgain() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:progress-schema", "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("""
                    create table submissions (
                        id bigint primary key, user_id bigint, problem_id bigint,
                        status varchar(16), created_at timestamp
                    )
                    """);
            statement.executeUpdate("insert into submissions (id, status) values (1, 'AC')");
            var schema = new ClassPathResource("schema-h2.sql");
            ScriptUtils.executeSqlScript(connection, schema);
            statement.executeUpdate("update submissions set passed_cases = 1, total_cases = 2");
            statement.executeUpdate("insert into submissions (id, status) values (2, 'PENDING')");
            ScriptUtils.executeSqlScript(connection, schema);

            try (var rows = statement.executeQuery("select passed_cases, total_cases from submissions order by id")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("passed_cases")).isEqualTo(1);
                assertThat(rows.getInt("total_cases")).isEqualTo(2);
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("passed_cases")).isZero();
                assertThat(rows.getInt("total_cases")).isZero();
            }
        }
    }
}
