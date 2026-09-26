# queryDbOnDemand — 按需 SQL 列裁剪

[English](README.md) | **中文**

在 [calculatePrice](../calculatePrice/README.zh.md) 场景的基础上扩展。展示如何把请求 BOM 一路下推到 SQL，让数据库只读取被请求的列。

展示的技术点：

1. 把请求 BOM 的键直接转换为 SQL `SELECT` 的列清单；
2. 通用 `AbstractBomDao<T>`：从 JPA 注解与 `Bom` 字段名映射推导表名/列名元数据；
3. 复用 `@Table` / `@Column`（Jakarta Persistence）覆盖默认的名称转换；
4. `CamelRowMapper` 把 `ResultSet` 映射回 POJO，并支持 `@Column` 指定的列名；
5. 把 DAO 接入 Spring 的 `JdbcTemplate`，并把阻塞式 JDBC 调用包装为响应式 `Mono`。

## 场景

用户接口返回 `t_user` 的部分或全部列：

| BOM 字段 | 列 | 说明 |
|----------|----|------|
| `userId` | `user_id` | 查询键 |
| `name` | `name` | 用户名 |
| `avatar` | `avatar` | 头像地址 |
| `roleId` | `role_id` | 角色 id |
| `password` | `password` | 敏感字段，通常不请求 |

客户端只声明需要的字段。DAO 据此拼出 `select <请求的列> from t_user where user_id = ?`，未请求的列不会被数据库读取。

## 项目结构

```
src/main/java/io/github/hanbernate/jsonbom/
├── example/
│   ├── User.java                     # 实体：@Table("t_user")，字段映射到列
│   └── UserRepository.java           # 由请求 BOM 拼装 SQL
└── spring/
    ├── AbstractBomDao.java           # 通用 BOM -> SQL 列裁剪 + JDBC 辅助
    └── CamelRowMapper.java           # ResultSet -> POJO 行映射器

src/test/java/io/github/hanbernate/jsonbom/example/
└── UserRepositoryTest.java           # 基于 H2 的按需列测试
```

## 实体与 Repository

`User` 声明表名，并让映射器推导列名：

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

`UserRepository` 继承通用 DAO，把 BOM 与 id 组合后拼装查询：

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

## 核心抽象

`AbstractBomDao<T>` 从泛型父类解析实体类型，并预先计算 BOM 字段名到列名的映射：

```java
protected AbstractBomDao(){
    this.mappedClass = (Class<T>) ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    this.defaultRowMapper = CamelRowMapper.newInstance(this.mappedClass);
    this.tableName = getTableName(this.mappedClass);
    this.bom2column = Arrays.stream(this.mappedClass.getDeclaredFields())
        .collect(Collectors.toMap(f -> f.getName(), f -> getColumnName(f)));
}
```

列清单只由能解析到已知字段的 BOM 键构成：

```java
protected String getSelectSql(Bom bom){
    String columns = bom.keySet().stream().map(bomName -> bom2column.get(bomName))
        .filter(Objects::nonNull)
        .collect(Collectors.joining(","));
    return "select " + columns + " from " + this.tableName;
}
```

名称转换默认使用 `UpperCamel -> lower_underscore`（Guava `CaseFormat`），并可由 `@Table` / `@Column` 覆盖：

```java
private static String getTableName(Class<?> clazz){
    return Optional.ofNullable(clazz.getAnnotation(Table.class))
        .map(Table::name)
        .orElse(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, clazz.getSimpleName()));
}
```

`CamelRowMapper` 用同一套映射把 `ResultSet` 的列名（转小写）与字段匹配，并通过 setter 或字段写入 POJO：

```java
String column = JdbcUtils.lookupColumnName(rsmd, index).toLowerCase();
FieldSetter<T> setter = this.mappedFields.get(column);
if (setter != null) {
    Object value = JdbcUtils.getResultSetValue(rs, index, setter.field.getType());
    setter.set(result, value);
}
```

## 请求与返回

客户端请求 BOM：

```json
{
  "userId": "",
  "name": "",
  "avatar": ""
}
```

生成的 SQL：

```sql
select user_id,name,avatar from t_user WHERE user_id = ?
```

返回：

```json
{
  "userId": 1001,
  "name": "Alice",
  "avatar": "avatar1.png"
}
```

只请求 `{ "name": "" }` 时生成 `select name from t_user ...`，返回的 `User` 其余字段均为 `null`。

## 实现细节

- BOM 是列裁剪的唯一依据：键变成 SQL 列，WHERE 条件的键则作为带类型的 JDBC 参数单独传入。
- `findOne` 接收 `Map.Entry<?, Integer>[]` 参数，把值与它的 `java.sql.Types` 一起传递，并在查询意外返回多行时打印告警。
- 默认列映射在构造器中只计算一次，因此 `getSelectSql` 开销很小，只过滤掉非实体字段的键。
- 列裁剪与行映射都遵循 `@Column(name = ...)` 与 `@Table(name = ...)`，使 DAO 与 JPA 风格实体保持同步。
- 当属性没有 write 方法时，`CamelRowMapper` 回退为直接字段访问，因此不可变风格的字段也能映射。
- repository 保持响应式契约：阻塞的 `JdbcTemplate` 调用被包装为 `Mono.justOrEmpty`，并在拼装查询前把 id 与 BOM 组合。
- 本示例只依赖 `spring-jdbc` / `spring-beans` 与 Guava，不涉及任何 ORM。

## 测试

`UserRepositoryTest` 基于内嵌 H2 数据库运行，覆盖：

| 测试 | 覆盖点 |
|------|--------|
| `findByUserId_existingUser_shouldReturnUser` | 被请求的列有值，未请求的列为 `null` |
| `findByUserId_nonExistingUser_shouldReturnEmpty` | 记录不存在时以空流完成 |
| `findByUserId_shouldSelectRequestedColumnsOnly` | 只选择并映射被请求的列 |

运行：

```
./gradlew :queryDbOnDemand:test
```

## 相关示例

- [calculatePrice](../calculatePrice/README.zh.md) — 基础按需计算
- [enumMap](../enumMap/README.zh.md) — 枚举驱动的模型注册
- [mcpServer](../mcpServer/README.zh.md) — 面向 MCP 工具的 BOM 查询 Schema
