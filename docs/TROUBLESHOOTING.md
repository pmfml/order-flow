# Troubleshooting — OrderFlow

Guide to errors encountered during development and their solutions.

---

## Summary

1. [Spring Boot 4.1 — DataJpaTest and AutoConfigureTestDatabase packages not found](#1-spring-boot-41--datajpatest-and-autoconfiguretestdatabase-packages-not-found)
2. [Hibernate — @CreationTimestamp is null in tests](#2-hibernate--creationtimestamp-is-null-in-tests)

3. [Spring Boot 4.1 — WebMvcTest package not found](#3-spring-boot-41--webmvctest-package-not-found)
4. [Spring Boot 4.1 — ObjectMapper bean not found (Jackson 3 migration)](#4-spring-boot-41--objectmapper-bean-not-found-jackson-3-migration)
5. [Hibernate Validator — @Valid on container is deprecated](#5-hibernate-validator--valid-on-container-is-deprecated)

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
