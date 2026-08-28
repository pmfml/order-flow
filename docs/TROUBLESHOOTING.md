# Troubleshooting — OrderFlow

Guide to errors encountered during development and their solutions.

---

## Summary

1. [Spring Boot 4.1 — DataJpaTest and AutoConfigureTestDatabase packages not found](#1-spring-boot-41--datajpatest-and-autoconfiguretestdatabase-packages-not-found)
2. [Hibernate — @CreationTimestamp is null in tests](#2-hibernate--creationtimestamp-is-null-in-tests)

3. [Spring Boot 4.1 — WebMvcTest package not found](#3-spring-boot-41--webmvctest-package-not-found)
4. [Spring Boot 4.1 — ObjectMapper bean not found (Jackson 3 migration)](#4-spring-boot-41--objectmapper-bean-not-found-jackson-3-migration)
5. [Hibernate Validator — @Valid on container is deprecated](#5-hibernate-validator--valid-on-container-is-deprecated)
6. [Spring Boot 4.1 — DataMongoTest package not found](#6-spring-boot-41--datamongotest-package-not-found)
7. [Spring Boot 4.x — Flyway migrations never run with only flyway-core](#7-spring-boot-4x--flyway-migrations-never-run-with-only-flyway-core)
8. [Spring Boot 4.0 — spring.data.mongodb.uri is silently ignored](#8-spring-boot-40--springdatamongodburi-is-silently-ignored)
9. [Test application.properties shadows the main one instead of merging](#9-test-applicationproperties-shadows-the-main-one-instead-of-merging)
10. [Spring Boot 3.4+ — @MockBean deprecated in favour of @MockitoBean](#10-spring-boot-34--mockbean-deprecated-in-favour-of-mockitobean)

---

## 1. Spring Boot 4.1 — DataJpaTest and AutoConfigureTestDatabase packages not found

### Error

```text
[ERROR] .../OrderRepositoryTest.java:[8,56] cannot find symbol
[ERROR]   symbol:   class AutoConfigureTestDatabase
[ERROR]   location: package org.springframework.boot.test.autoconfigure.jdbc
[ERROR] .../OrderRepositoryTest.java:[9,59] package org.springframework.boot.test.autoconfigure.orm.jpa does not exist
```

### Cause

Spring Boot 4.1 modularized and reorganized its test autoconfigure packages. The annotations `@DataJpaTest` and `@AutoConfigureTestDatabase` were moved to new packages.

### Solution

Update the imports in the test classes:

```java
// ❌ Before
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

// ✅ After
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
```

---

## 2. Hibernate — @CreationTimestamp is null in tests

### Error

When asserting that a newly saved entity has its `@CreationTimestamp` or `@UpdateTimestamp` fields populated, the assertion fails because the value is `null`.

```text
[ERROR] Failures: 
[ERROR]   OrderRepositoryTest.shouldSaveAndRetrieveOrderWithItems:54 
Expecting actual not to be null
```

### Cause

Hibernate's `@CreationTimestamp` and `@UpdateTimestamp` are populated during the `flush` phase of the persistence context, not immediately upon calling `repository.save()`.

### Solution

In the integration test, inject a `TestEntityManager`, call `flush()` and `clear()`, and then retrieve the entity from the database before performing the assertions.

```java
// ✅ Solution
@Autowired
private TestEntityManager entityManager;

@Test
void shouldSaveAndRetrieveOrderWithItems() {
    Order saved = orderRepository.save(order);
    
    // Force synchronization with the database and clear the L1 cache
    entityManager.flush();
    entityManager.clear();

    // Re-read the entity to get the materialized timestamp values
    Order found = orderRepository.findById(saved.getId()).orElseThrow();

    assertThat(found.getCreatedAt()).isNotNull();
}
```

---

## 3. Spring Boot 4.1 — WebMvcTest package not found

### Error

```text
[ERROR] .../OrderControllerTest.java:[11,63] package org.springframework.boot.test.autoconfigure.web.servlet does not exist
[ERROR] .../OrderControllerTest.java:[27,2] cannot find symbol
[ERROR]   symbol: class WebMvcTest
```

### Cause

Similar to `@DataJpaTest`, Spring Boot 4.1 modularized the web slice test annotations into their own packages and modules.

### Solution

1. Add the specific test starter for WebMVC to your `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

2. Update the import in the test class:
```java
// ❌ Before
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

// ✅ After
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
```

---

## 4. Spring Boot 4.1 — ObjectMapper bean not found (Jackson 3 migration)

### Error

```text
No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available
```
or
```text
package com.fasterxml.jackson.core does not exist
package com.fasterxml.jackson.databind does not exist
```

### Cause

Spring Boot 4.1 migrated the default JSON processor from Jackson 2 (`com.fasterxml.jackson`) to Jackson 3 (`tools.jackson`). If you manually imported `jackson-databind` from the old group or are trying to autowire `com.fasterxml.jackson.databind.ObjectMapper`, it will fail because Spring now exposes `tools.jackson.databind.ObjectMapper`.

### Solution

1. Remove any explicit `com.fasterxml.jackson` dependencies from `pom.xml`. `spring-boot-starter-web` already brings the correct Jackson 3 version.
2. Update all imports in Java files:
```java
// ❌ Before
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

// ✅ After
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
```

---

## 5. Hibernate Validator — @Valid on container is deprecated

### Error

```text
WARN --- o.h.v.i.m.a.CascadingMetaDataBuilder : HV000271: Using `@Valid` on a container (java.util.List) is deprecated. You should apply the annotation on the type argument(s).
```

### Cause

Placing `@Valid` directly on a Collection (e.g., `List`, `Set`) is deprecated in newer versions of Hibernate Validator (Jakarta Validation). The correct syntax is to place the annotation on the *type argument* of the generic container.

### Solution

Move `@Valid` inside the angle brackets `< >`:

```java
// ❌ Before
@Valid
List<OrderItemRequest> items;

// ✅ After
List<@Valid OrderItemRequest> items;
```

---

## 6. Spring Boot 4.1 — DataMongoTest package not found

### Error

```text
[ERROR] .../ProductRepositoryTest.java:[8,62] package org.springframework.boot.test.autoconfigure.data.mongo does not exist
```

### Cause

As with `@DataJpaTest` and `@WebMvcTest`, the MongoDB slice test annotation was moved to a new module/package in Spring Boot 4.1.

### Solution

1. Ensure you have the `spring-boot-starter-data-mongodb-test` dependency in `pom.xml`.
2. Update the import in your test classes:

```java
// ❌ Before
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

// ✅ After
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
```

---

## 7. Spring Boot 4.x — Flyway migrations never run with only flyway-core

### Error

No error. The application starts, and that is what makes this one costly.

Flyway logs nothing, `flyway_schema_history` is never created, and the schema stays
empty. The failure only becomes visible once something depends on the schema being
there:

```text
Failed to initialize JPA EntityManagerFactory: Unable to build Hibernate SessionFactory
nested exception is org.hibernate.tool.schema.spi.SchemaManagementException:
Schema validation: missing table [order_items]
```

With `ddl-auto=update` or `create-drop` instead of `validate`, there is no symptom at
all: Hibernate quietly generates the schema and the migrations are simply dead files.

### Cause

Spring Boot 4.x ships auto-configuration in dedicated modules rather than one large
`spring-boot-autoconfigure` jar. `org.flywaydb:flyway-core` puts Flyway on the
classpath but carries no `FlywayAutoConfiguration`, so nothing ever triggers the
migration on startup.

To confirm, check whether any jar on the classpath actually provides the class:

```bash
for j in $(find ~/.m2/repository/org/springframework/boot -name "*.jar"); do
  unzip -l "$j" 2>/dev/null | grep -q "FlywayAutoConfiguration" && echo "FOUND: $j"
done
# no output means the auto-configuration is absent
```

### Solution

Depend on the Boot module, which provides the auto-configuration and pulls
`flyway-core` in transitively:

```xml
<!-- ❌ Before: Flyway on the classpath, but never executed -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<!-- ✅ After -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-flyway</artifactId>
</dependency>
```

Keep `flyway-database-postgresql` at runtime scope for the PostgreSQL dialect.

Then set `ddl-auto=validate` so Flyway owns the schema and Hibernate only asserts that
the entities match it. That turns a silent divergence into a startup failure:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

---

## 8. Spring Boot 4.0 — spring.data.mongodb.uri is silently ignored

### Error

Again no startup error. The application connects to the *default* MongoDB address
instead of the configured one, and only fails when a query runs:

```text
com.mongodb.MongoTimeoutException: Timed out while waiting for a server that matches
ReadPreferenceServerSelector{readPreference=primary}. Client view of cluster state is
{type=UNKNOWN, servers=[{address=localhost:27017, type=UNKNOWN, state=CONNECTING,
exception={com.mongodb.MongoSocketOpenException: Exception opening socket},
caused by {java.net.ConnectException: Connection refused}}]
```

The giveaway is the port: `27017` is the driver default, not the value configured in
`application.properties`.

### Cause

`spring.data.mongodb.*` was deprecated in favour of `spring.mongodb.*` in Boot 4.0,
at deprecation level **`error`**. Properties deprecated at that level are no longer
bound at all — they are ignored rather than warned about, so the connection settings
silently revert to their defaults.

Tests do not catch this: `@ServiceConnection` supplies the container's connection
details programmatically and never reads the property.

### Solution

```properties
# ❌ Before: ignored, falls back to localhost:27017
spring.data.mongodb.uri=mongodb://localhost:27018/orderflow

# ✅ After
spring.mongodb.uri=mongodb://localhost:27018/orderflow
```

The same rename applies to `host`, `port`, `database`, `username`, `password`,
`authentication-database`, `replica-set-name` and `protocol`.

Because this class of failure is invisible, it is worth validating every property key
against the metadata Boot ships, rather than discovering them one at a time:

```bash
# Every configuration key Boot knows about, with deprecation level
python3 - <<'PY'
import json, zipfile
from pathlib import Path
for jar in (Path.home()/".m2/repository").rglob("spring-boot*.jar"):
    try:
        with zipfile.ZipFile(jar) as z:
            if "META-INF/spring-configuration-metadata.json" not in z.namelist():
                continue
            data = json.loads(z.read("META-INF/spring-configuration-metadata.json"))
    except Exception:
        continue
    for p in data.get("properties", []):
        if p.get("deprecated"):
            d = p.get("deprecation", {})
            print(p["name"], "->", d.get("replacement"), "level=", d.get("level"))
PY
```

---

## 9. Test application.properties shadows the main one instead of merging

### Error

A bean fails to start in tests over a property that is plainly present in `src/main/resources/application.properties`:
```text
Caused by: org.springframework.util.PlaceholderResolutionException:
Could not resolve placeholder 'orderflow.outbox.send-timeout-ms'
in value "${orderflow.outbox.send-timeout-ms}"
```
Or, Kafka integration tests (e.g. `SagaReactionIntegrationTest`) intermittently fail/timeout during message consumption:
```text
expected: CONFIRMED
 but was: PENDING within 30 seconds.
```
While checking the consumer logs, we observe that the offset reset defaults to `latest` and skips partition offsets:
```text
Resetting offset for partition payment.authorized-0 to position FetchPosition{offset=1...}
```

### Cause

`src/main/resources/application.properties` and `src/test/resources/application.properties` both resolve to the same classpath resource, `classpath:/application.properties`. The test classes directory comes first on the test classpath, so the test copy is the only one loaded. It does not merge with the main file, it replaces it.

Because of this, all main configurations are lost in tests unless explicitly repeated in `src/test/resources/application.properties`. For Kafka, this caused consumer configurations (like `spring.kafka.consumer.auto-offset-reset=earliest` and serializers/deserializers) to fall back to default values. When a test produced a message before partition assignment finished on the Testcontainers broker, the default `latest` policy caused the consumer to ignore the message, leading to a timeout.

### Solution

1. Repeat in the test file whatever the beans/services require. For Kafka, ensure all default serialization and offset reset properties are declared in the test-scoped file:
```properties
# src/test/resources/application.properties
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.consumer.group-id=order-service
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

2. When only a couple of keys need to differ, prefer overriding them per test class so the main configuration stays in play:
```java
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class FlywayMigrationTest { }
```

Note that `spring.task.scheduling.enabled` does **not** exist, so it cannot be used to silence `@Scheduled` beans during tests. Either give the schedule an interval long enough never to fire, or move `@EnableScheduling` onto a `@Profile`-gated configuration class.

---

## 10. Spring Boot 3.4+ — @MockBean deprecated in favour of @MockitoBean

### Error

```text
warning: [removal] MockBean in org.springframework.boot.test.mock.mockito has been deprecated
```

or at runtime with Spring Boot 4.x:

```text
package org.springframework.boot.test.mock.mockito does not exist
```

### Cause

Starting in Spring Boot 3.4, the `@MockBean` and `@SpyBean` annotations from
`org.springframework.boot.test.mock.mockito` were deprecated. In Spring Boot 4.x, the
old package was removed entirely. The replacement annotations live in a new package and
have different bean-matching semantics.

### Solution

Update imports and annotations:

```java
// ❌ Before
import org.springframework.boot.test.mock.mockito.MockBean;

@MockBean
private RateLimiter rateLimiter;

// ✅ After
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@MockitoBean
private RateLimiter rateLimiter;
```

Key differences to be aware of:
- `@MockitoBean` matches beans **by name** (the field name must match the bean name
  in the application context), whereas `@MockBean` matched by type.
- If your bean is registered with a specific name (e.g., `customRedisRateLimiter`),
  your test field must use the same name.

