# jsonschema-java-parser 技术方案

## 1. 项目定位

基于 Java APT（Annotation Processing Tool）在**编译期**将 Java 实体类解析成 [JSON Schema](https://json-schema.org/)，输出为 JSON 文件或 Java 常量类，供前端、文档、契约校验等场景使用。

与运行时反射方案（victools、mbknor-jackson-jsonSchema 等）的差异：

| 维度 | 运行时反射 | APT 编译期（本项目） |
| --- | --- | --- |
| 启动开销 | 首次反射较慢 | 零运行时开销 |
| 错误暴露 | 运行时抛出 | 编译期失败 |
| 类加载依赖 | 需要类加载器 | 不依赖类加载 |
| GraalVM Native Image | 需要额外配置 | 天然友好 |
| 实现复杂度 | 简单（反射 API） | 较高（`TypeMirror` API） |

## 2. 目标与非目标

### 2.1 目标
- 支持 Java 8 及以上（含 11 / 17 / 21）
- 输出 JSON Schema **Draft 2020-12**（兼容 OpenAI Structured Outputs / Function Calling 协议子集）
- 支持嵌套类、泛型集合、Map、枚举、继承
- 支持递归 / 互引用类型（通过 `$ref` + `$defs`）
- 集成主流注解：Jackson（`@JsonProperty`/`@JsonIgnore` 等）、JSR-303 校验注解（`@NotNull`/`@Size`/`@Pattern` 等）
- 自定义注解扩展点：`@JsonSchema`、`@JsonSchemaProperty`、`@JsonSchemaIgnore`
- 输出形态可选：JSON 文件、Java 常量类、运行时查找用的 Registry 类

### 2.1.1 OpenAI 兼容模式
OpenAI Structured Outputs / Function Calling 使用的是 JSON Schema Draft 2020-12 的**严格子集**。本项目通过 `@JsonSchema(openaiCompatible = true)` 或编译参数 `-AjsonschemaOpenaiCompatible=true` 开启，开启后：

- **类型**：仅产出 `string` / `number` / `integer` / `boolean` / `object` / `array`；`null` 表达为 `"type": ["X", "null"]` 而非 `nullable`
- **必填**：`properties` 下所有字段强制写入 `required`（非必填字段以 `["X","null"]` 表达）
- **`additionalProperties`**：每个 object 强制输出 `"additionalProperties": false`
- **组合**：仅使用 `anyOf` + `$ref` + `$defs`；不输出 `oneOf` / `allOf`
- **过滤掉**的校验关键字：`minLength` / `maxLength` / `pattern` / `format` / `minimum` / `maximum` / `multipleOf` / `minItems` / `maxItems` / `uniqueItems` 等（仍可保留在 `description` 文本中以提示模型）
- **规模检查**：处理器在生成完成后做静态扫描，属性总数 > 100 或嵌套深度 > 5 时**编译期报错**
- **继承策略**：自动从 `ALL_OF` 强制切换为 `FLATTEN`（因为 strict 模式不支持 `allOf`）

### 2.2 非目标（V1 不做）
- JSON Schema 校验运行时（仅生成 Schema，不做校验）
- Kotlin / Scala / Groovy 源码支持
- Gradle 插件（首版以 APT 标准方式集成，Gradle/Maven 通用）
- OpenAPI 直接产物（可后续扩展）

## 3. 模块划分

采用 Maven 多模块，命名前缀 `jsonschema-java-parser-`：

```
jsonschema-java-parser/
├── pom.xml                                       # 父 POM，统一版本与依赖管理
├── jsonschema-java-parser-annotations/           # 注解定义，使用方编译期依赖
├── jsonschema-java-parser-core/                  # 类型映射、Schema 模型、JSON 序列化
├── jsonschema-java-parser-processor/             # APT 处理器主体
├── jsonschema-java-parser-tests/                 # 集成测试（compile-testing）
└── jsonschema-java-parser-examples/              # 示例工程
```

依赖关系：

```
annotations  ← processor → core
                                  ↑
                              tests / examples
```

`annotations` 模块**零依赖**，体积小，避免污染使用方运行时 classpath。
`processor` 模块在使用方仅作为 `annotationProcessor` / `provided` 范围引入。

## 4. 核心注解设计

### 4.1 `@JsonSchema`（类级别）

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface JsonSchema {
    String id() default "";                       // $id
    String title() default "";                    // title
    String description() default "";              // description
    Draft draft() default Draft.DRAFT_2020_12;    // 输出草案版本
    String outputPath() default "";               // 自定义输出路径，空则用默认
    boolean additionalProperties() default false; // 是否允许额外字段
    boolean openaiCompatible() default false;     // 启用 OpenAI 兼容模式（见 2.1.1）
}
```

### 4.2 `@JsonSchemaProperty`（字段级别）

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface JsonSchemaProperty {
    String name() default "";          // 重命名字段
    String description() default "";
    String format() default "";        // email / date-time / uri 等
    boolean required() default false;
    String defaultValue() default "";
    String[] examples() default {};
}
```

### 4.3 `@JsonSchemaIgnore`（字段级别）

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface JsonSchemaIgnore {}
```

### 4.4 `@JsonSchemaDefinition`（类级别，标记可被复用的 `$defs` 节点）

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface JsonSchemaDefinition {
    String name() default "";  // $defs 下的 key，空则用类的简单名
}
```

## 5. 类型映射规则

| Java 类型 | JSON Schema |
| --- | --- |
| `String`, `char`, `Character` | `{"type": "string"}` |
| `boolean`, `Boolean` | `{"type": "boolean"}` |
| `byte`/`short`/`int`/`long` 及包装类、`BigInteger` | `{"type": "integer"}` |
| `float`/`double` 及包装类、`BigDecimal` | `{"type": "number"}` |
| `java.util.Date`, `Instant`, `OffsetDateTime`, `ZonedDateTime`, `LocalDateTime` | `{"type": "string", "format": "date-time"}` |
| `LocalDate` | `{"type": "string", "format": "date"}` |
| `LocalTime` | `{"type": "string", "format": "time"}` |
| `UUID` | `{"type": "string", "format": "uuid"}` |
| `URI`, `URL` | `{"type": "string", "format": "uri"}` |
| `enum` | `{"type": "string", "enum": [...]}` |
| `T[]`, `Collection<T>`, `Iterable<T>` | `{"type": "array", "items": <T>}` |
| `Set<T>` | `{"type": "array", "items": <T>, "uniqueItems": true}` |
| `Map<String, V>` | `{"type": "object", "additionalProperties": <V>}` |
| `Optional<T>` | `<T>`（且字段从 `required` 中剔除） |
| 自定义实体 | `{"$ref": "#/$defs/<TypeKey>"}` |

类型映射在 `core` 模块由 `TypeMapper` 接口实现，使用方可通过 SPI 注册自定义映射。

## 6. APT 处理流程

```
┌────────────────────────────────────────────────────────────┐
│  Round 1: 收集 @JsonSchema / @JsonSchemaDefinition 标记类     │
└────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────┐
│  Round 2: 构建类型图（TypeGraph），解析字段、泛型、继承          │
│           递归遇到自定义类型时按需扩展节点                       │
└────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────┐
│  Round 3: 翻译为 SchemaNode 树（core 模块的中间模型）            │
│           应用 Jackson / JSR-303 注解 → Schema 关键字          │
└────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────┐
│  Round 4: 序列化输出                                          │
│   - Filer 写入 META-INF/jsonschema/<fqcn>.json               │
│   - 可选生成 SchemaRegistry.java                              │
└────────────────────────────────────────────────────────────┘
```

### 6.1 关键 APT API
- `javax.annotation.processing.Processor` / `AbstractProcessor`
- `javax.annotation.processing.RoundEnvironment#getElementsAnnotatedWith`
- `javax.lang.model.element.TypeElement` / `VariableElement` / `ExecutableElement`
- `javax.lang.model.type.TypeMirror` / `DeclaredType` / `TypeKind`
- `javax.annotation.processing.Filer#createResource(StandardLocation.CLASS_OUTPUT, ...)`
- `javax.annotation.processing.Messager` 用于编译期日志与错误

### 6.2 `@SupportedSourceVersion`
声明为 `SourceVersion.RELEASE_8`，但通过 `@SupportedOptions` 与重写 `getSupportedSourceVersion()` 返回 `SourceVersion.latest()` 以避免新版本编译警告。

### 6.3 `@SupportedAnnotationTypes`
显式列出本项目注解 + 可选的 Jackson / JSR-303 注解全限定名，减少 round 触发开销。

## 7. 关键难点设计

### 7.1 递归 / 互引用
- 每个被引用的自定义类型统一登记到 `$defs`，key 使用稳定的 FQN（如 `com.example.Foo`）
- 字段处用 `{"$ref": "#/$defs/com.example.Foo"}` 引用
- 使用 `Set<String> visited` 防止重复展开

### 7.2 泛型
- 通过 `DeclaredType#getTypeArguments()` 拿到实参 `TypeMirror`
- 类型变量 `T`（`TypeVariable`）作为字段类型时：
  - V1：退化为 `{}`（任意 JSON 值）并在 `description` 标注 `unresolved type variable`
  - V2：要求宿主类用 `@JsonSchema(genericBindings = { ... })` 显式绑定

### 7.3 继承
- 沿 `TypeElement#getSuperclass()` 向上递归收集字段
- 输出策略二选一（通过 `@JsonSchema(inheritance = FLATTEN | ALL_OF)`）：
  - `FLATTEN`：父子字段平铺
  - `ALL_OF`：用 `allOf: [{"$ref": "#/$defs/Parent"}, { properties: { 子字段 } }]`

### 7.4 多态（V2 计划）
识别 Jackson 的 `@JsonTypeInfo` + `@JsonSubTypes`，输出 `oneOf` + discriminator 字段。

### 7.5 枚举
- `enum` 常量名列入 `enum` 数组
- 若枚举字段标了 `@JsonValue` 的方法返回值，使用该值（通过 APT 检查方法返回类型字面值，复杂场景 V2 支持）

### 7.6 注解集成

| 来源注解 | 映射到 |
| --- | --- |
| `@NotNull`, `@NonNull` | `required` 列表 |
| `@NotBlank`, `@NotEmpty` | `minLength: 1` / `minItems: 1` |
| `@Size(min, max)` | `minLength`/`maxLength` 或 `minItems`/`maxItems` |
| `@Min`, `@Max`, `@DecimalMin`, `@DecimalMax` | `minimum`/`maximum` |
| `@Pattern` | `pattern` |
| `@Email` | `format: email` |
| `@Positive`, `@PositiveOrZero` | `exclusiveMinimum: 0` / `minimum: 0` |
| `@JsonProperty("xxx")` | 字段重命名 |
| `@JsonIgnore` | 跳过 |
| `@JsonPropertyDescription` | `description` |

注解处理在 `core` 模块由策略链实现，每个策略实现 `AnnotationMapper`，按顺序应用。

## 8. 输出策略

### 8.1 JSON 文件（默认）
路径：`META-INF/jsonschema/<fqcn>.json`，由 `Filer#createResource` 写入到 `CLASS_OUTPUT`，最终进入 jar 包，可在运行时通过 `ClassLoader#getResourceAsStream` 加载。

### 8.2 Java 常量类（可选）
通过编译参数 `-AjsonschemaGenerateConstants=true` 触发，生成：

```java
public final class GeneratedSchemas {
    public static final String USER = "{...}";
    // ...
}
```

### 8.3 Registry 类（可选）
`-AjsonschemaGenerateRegistry=true`，生成：

```java
public final class JsonSchemaRegistry {
    public static String get(Class<?> type) { ... }
}
```

## 9. 编译期配置

通过 `-A<key>=<value>` 传入，由 `@SupportedOptions` 声明：

| Key | 默认 | 说明 |
| --- | --- | --- |
| `jsonschemaDraft` | `2020-12` | 当前仅 `2020-12` |
| `jsonschemaOutputDir` | `META-INF/jsonschema` | 输出目录（相对 `CLASS_OUTPUT`），全限定类名作为文件名 |
| `jsonschemaPretty` | `true` | 是否格式化 |
| `jsonschemaInheritance` | `ALL_OF` | `FLATTEN` / `ALL_OF`（OpenAI 兼容模式强制 `FLATTEN`） |
| `jsonschemaIncludeJackson` | `true` | 是否启用 Jackson 注解集成 |
| `jsonschemaIncludeJsr303` | `true` | 是否启用 JSR-303 注解集成 |
| `jsonschemaOpenaiCompatible` | `false` | 全局开启 OpenAI 兼容模式（覆盖 `@JsonSchema(openaiCompatible)`） |
| `jsonschemaGenerateConstants` | `false` | 同 8.2 |
| `jsonschemaGenerateRegistry` | `false` | 同 8.3 |

## 10. JSON 序列化

`core` 模块自带极简 JSON Writer（无外部依赖），原因：

- `annotations` / `processor` 不希望传递 Jackson/Gson 依赖
- Schema 输出结构简单（嵌套 map + list + 标量），手写 < 300 行可覆盖
- 避免使用方 classpath 冲突

序列化结构基于内部 `SchemaNode`（不可变）：

```java
public final class SchemaNode {
    // type: object/array/string/...
    // properties: Map<String, SchemaNode>
    // ref: String
    // enumValues, items, additionalProperties, formatStr ...
}
```

## 11. 测试策略

- **单测**：`core` 模块的类型映射、注解处理策略
- **APT 集成测试**：使用 [`com.google.testing.compile`](https://github.com/google/compile-testing) 在 `tests` 模块下，对各种实体类样例触发编译并断言生成产物
- **示例工程**：`examples/` 下用真实项目结构验证 Maven 集成
- **CI 矩阵**：JDK 8 / 11 / 17 / 21 × Maven 3.6+/3.9+

## 12. 构建与发布

- 父 POM 锁定 Java 8 `source`/`target`
- 使用 `maven-compiler-plugin` 3.13+
- GAV：`io.github.kurok1.jsonschema:<module>`

### 12.1 GitHub Packages 发布
- 发布目标：`https://maven.pkg.github.com/kurok1/jsonschema-java-parser`
- 仅 `annotations` / `core` / `processor` 三个库模块发布（`tests` / `examples` 通过 `maven.deploy.skip=true` / `maven.install.skip=true` 跳过）
- 鉴权使用 GitHub PAT（`read:packages` 读、`write:packages` 推）

### 12.2 GitHub Actions
- `.github/workflows/ci.yml` —— push / PR 触发，JDK 8 / 11 / 17 / 21 矩阵跑 `mvn -B verify`
- `.github/workflows/publish.yml` —— **tag 触发**（`push: tags: ['v*']`）或手动 `workflow_dispatch(version)` 触发：
  1. 从 tag（或 input）解析版本号，`mvn versions:set` 改写 POM
  2. `mvn verify` 全量构建测试
  3. setup-java 注入 `server-id=github` 与 `GITHUB_TOKEN`，`mvn deploy` 推送 `annotations` / `core` / `processor` 三个库模块到 GitHub Packages
  4. `softprops/action-gh-release@v2` 用 tag 自动建 GitHub Release，挂上库 jar，附自动生成的 release notes

使用方集成（README 有完整示例）：

```xml
<repositories>
    <repository>
        <id>github-kurok1</id>
        <url>https://maven.pkg.github.com/kurok1/jsonschema-java-parser</url>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.kurok1.jsonschema</groupId>
    <artifactId>jsonschema-java-parser-annotations</artifactId>
    <version>${ver}</version>
</dependency>
<build>
    <plugins>
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.kurok1.jsonschema</groupId>
                        <artifactId>jsonschema-java-parser-processor</artifactId>
                        <version>${ver}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## 13. 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 泛型类型变量解析复杂 | 部分场景生成不完整 Schema | V1 退化为 `{}` + 警告，V2 支持显式绑定 |
| Jackson 多态 `@JsonTypeInfo` 形态多 | 输出不准确 | V1 不支持，V2 单独迭代 |
| APT 在某些 IDE（如 IDEA）需手动开启 | 用户体验下降 | README 提供集成指引 |
| Lombok 与 APT 顺序问题 | 字段在 Lombok 之前可能缺 getter，但本项目读字段不读 getter，影响较小 | 文档说明 |
| 增量编译可能漏触发 | 输出落后于源码 | 通过 `Filer` 注册 originating elements，让 build 工具感知依赖 |

## 14. 里程碑

- **M1（V0.1）**：注解定义 + 标量/集合/Map/枚举映射 + 基本 JSON 输出
- **M2（V0.2）**：递归 + `$ref/$defs` + 继承（`ALL_OF`）
- **M3（V0.3）**：JSR-303 + Jackson 注解集成
- **M4（V0.4）**：OpenAI 兼容模式（strict 子集 + 规模校验）、常量类 / Registry 生成、自定义 SPI 扩展
- **M5（V1.0）**：完整文档、示例工程、GitHub Actions（CI 矩阵 + GitHub Packages 发布）
- **V2 规划**：多态 `oneOf`、Kotlin data class、Gradle 插件、OpenAPI 互转

## 15. 代码规范

遵循用户全局规范：
- 不使用 `var`、不使用 `Object` 声明具体可知类型的变量
- 方法参数超过 4 个使用参数对象封装
- 显式声明 `ThreadPoolExecutor` 的核心 / 最大线程数（如确需异步处理 APT 流程）
- 内部模型类使用不可变设计，便于在多 round 中安全共享
