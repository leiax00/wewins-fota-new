package com.wewins.fota.infra.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationLayoutTest {

    private static final Pattern VERSION_PATTERN = Pattern.compile(".*/V(\\d+)__.+\\.sql");

    @Test
    void shouldContainExpectedMysqlMigrations() throws IOException {
        assertVersions("classpath*:db/migration/mysql/V*.sql", Set.of(1, 2, 4, 5));
    }

    @Test
    void shouldContainExpectedPostgresqlMigrations() throws IOException {
        assertVersions("classpath*:db/migration/postgresql/V*.sql", Set.of(1, 2, 3, 4, 5));
    }

    private void assertVersions(String locationPattern, Set<Integer> expectedVersions) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(locationPattern);

        assertTrue(resources.length > 0, "No Flyway migrations found for pattern: " + locationPattern);

        Set<Integer> actualVersions = Arrays.stream(resources)
                .map(Resource::getFilename)
                .map(this::extractVersion)
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(expectedVersions, actualVersions, "Unexpected Flyway migration versions for " + locationPattern);
    }

    private Integer extractVersion(String filename) {
        Matcher matcher = VERSION_PATTERN.matcher("/" + filename);
        assertTrue(matcher.matches(), "Invalid Flyway migration filename: " + filename);
        return Integer.parseInt(matcher.group(1));
    }
}
