# Troubleshooting — OrderFlow

Guide to errors encountered during development and their solutions.

---

## Summary

1. [Spring Boot 4.1 — DataJpaTest and AutoConfigureTestDatabase packages not found](#1-spring-boot-41--datajpatest-and-autoconfiguretestdatabase-packages-not-found)
2. [Hibernate — @CreationTimestamp is null in tests](#2-hibernate--creationtimestamp-is-null-in-tests)

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
