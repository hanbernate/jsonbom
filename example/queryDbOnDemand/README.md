# queryDbOnDemand — On-Demand SQL Projection

**English** | [中文](README.zh.md)

Builds on the [calculatePrice](../calculatePrice/README.md) scenario. It shows how to push the request BOM all the way down into SQL so that the database reads only the requested columns.

Technical points it demonstrates:

1. Turning a request BOM's keys directly into the SQL `SELECT` column list;
2. A generic `AbstractBomDao<T>` that derives table/column metadata from JPA annotations and a `Bom` field-name mapping;
3. Reusing `@Table` / `@Column` (Jakarta Persistence) to override the default name conversion;
4. A `CamelRowMapper` that maps the `ResultSet` back to the POJO, including `@Column`-named columns;
5. Wiring the DAO into Spring's `JdbcTemplate` and returning reactive `Mono` results from blocking JDBC calls.

## Scenario

A user endpoint returns some or all of the columns of `t_user`:

| BOM field | Column | Note |
|-----------|--------|------|
| `userId` | `user_id` | lookup key |
| `name` | `name` | user name |
| `avatar` | `avatar` | avatar URL |
| `roleId` | `role_id` | role id |
| `password` | `password` | secret, usually not requested |

The client declares only the fields it needs. The DAO builds `select <requested columns> from t_user where user_id = ?`, so unrequested columns are never read from the database.

## Project Layout

```
src/main/java/io/github/hanbernate/jsonbom/
├── example/
│   ├── User.java                     # entity: @Table("t_user"), fields map to columns
│   └── UserRepository.java           # builds SQL from the request BOM
└── spring/
    ├── AbstractBomDao.java           # generic BOM -> SQL projection + JDBC helper
    └── CamelRowMapper.java           # ResultSet -> POJO row mapper

src/test/java/io/github/hanbernate/jsonbom/example/
└── UserRepositoryTest.java           # H2-backed on-demand column test
```

## Entity and Repository

`User` declares the table and lets the mapper derive the columns:

```java
@Data
@Table(name = "t_user")
public class User {
    private Long id;
    private Long userId;
    private String name;
    private String avatar;
    private Long roleId;
    private String password;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`UserRepository` extends the generic DAO and zips the BOM with the id to build the query:

```java
@Repository
public class UserRepository extends AbstractBomDao<User> {
    public Mono<User> findByUserId(Mono<Bom> bomPublisher, Mono<Long> userIdPublisher) {
        return bomPublisher.zipWith(userIdPublisher).flatMap(t -> {
            String sql = getSelectSql(t.getT1()) + " WHERE user_id = ?";
            return Mono.justOrEmpty(findOne(sql,
                new Map.Entry[]{new AbstractMap.SimpleEntry<>(t.getT2(), Types.BIGINT)},
                getRowDefaultMapper()));
        });
    }
}
```

## Core Abstraction

`AbstractBomDao<T>` resolves the entity class from the generic superclass and pre-computes the BOM field name to column name mapping:

```java
protected AbstractBomDao(){
    this.mappedClass = (Class<T>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    this.defaultRowMapper = CamelRowMapper.newInstance(this.mappedClass);
    this.tableName = getTableName(this.mappedClass);
    this.bom2column = Arrays.stream(this.mappedClass.getDeclaredFields())
        .collect(Collectors.toMap(f -> f.getName(), f -> getColumnName(f)));
}
```

The column list is built only from the BOM keys that resolve to a known field:

```java
protected String getSelectSql(Bom bom){
    String columns = bom.keySet().stream().map(bomName -> bom2column.get(bomName))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(","));
    return "select " + columns + " from " + this.tableName;
}
```

Name conversion falls back to `UpperCamel -> lower_underscore` (Guava `CaseFormat`) and is overridden by `@Table` / `@Column`:

```java
private static String getTableName(Class<?> clazz){
    return Optional.ofNullable(clazz.getAnnotation(Table.class))
        .map(Table::name)
        .orElse(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clazz.getSimpleName()));
}
```

`CamelRowMapper` matches `ResultSet` column names (lower-cased) against the same mapping and writes each value into the POJO through its setter or field:

```java
String column = JdbcUtils.lookupColumnName(rsmd, index).toLowerCase();
FieldSetter<T> setter = this.mappedFields.get(column);
if (setter != null) {
    Object value = JdbcUtils.getResultSetValue(rs, index, setter.field.getType());
    setter.set(result, value);
}
```

## Request and Response

Client request BOM:

```json
{
  "userId": "",
  "name": "",
  "avatar": ""
}
```

Generated SQL:

```sql
select user_id,name,avatar from t_user WHERE user_id = ?
```

Response:

```json
{
  "userId": 1001,
  "name": "Alice",
  "avatar": "avatar1.png"
}
```

Requesting only `{ "name": "" }` produces `select name from t_user ...` and the returned `User` has `null` for every other field.

## Implementation Details

- The BOM is the single source of truth for projection: keys become SQL columns, and keys of the WHERE clause are passed separately as typed JDBC arguments.
- `findOne` takes `Map.Entry<?, Integer>[]` arguments so the value and its `java.sql.Types` are passed together, and warns when a query unexpectedly returns more than one row.
- The default column mapping is derived once in the constructor, so `getSelectSql` is allocation-light and only filters out keys that are not entity fields.
- `@Column(name = ...)` and `@Table(name = ...)` are respected in both the projection and the row mapping, keeping the DAO in sync with JPA-style entities.
- `CamelRowMapper` falls back to direct field access when a property has no write method, so immutable-style fields still map.
- The repository keeps the reactive contract: blocking `JdbcTemplate` calls are wrapped in `Mono.justOrEmpty` and the id is zipped with the BOM before the query is built.
- The example depends only on `spring-jdbc` / `spring-beans` and Guava; no ORM is involved.

## Tests

`UserRepositoryTest` runs against an embedded H2 database and covers:

| Test | Coverage |
|------|----------|
| `findByUserId_existingUser_shouldReturnUser` | requested columns are populated and unrequested ones stay `null` |
| `findByUserId_nonExistingUser_shouldReturnEmpty` | missing row completes empty |
| `findByUserId_shouldSelectRequestedColumnsOnly` | only the requested column is selected and mapped |

Run:

```
./gradlew :queryDbOnDemand:test
```

## Related Examples

- [Example conventions](../CONVENTIONS.md) — the coding conventions every example follows
- [calculatePrice](../calculatePrice/README.md) — basic on-demand calculation
- [enumMap](../enumMap/README.md) — enum-based model registry
- [mcpServer](../mcpServer/README.md) — MCP tool schema for BOM queries
