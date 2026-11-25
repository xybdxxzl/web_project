# 项目：Mini OJ 在线判题系统

## 技术栈
- 后端：Java 17 + Spring Boot 3 + Maven，Spring Data JPA，`spring-boot-starter-validation`，Lombok，H2（文件模式，可通过配置切换 MySQL）
- 认证与加密：不要引入 `spring-boot-starter-security`，仅引入 `org.springframework.security:spring-security-crypto` 使用 `BCryptPasswordEncoder`；登录使用 HttpSession，配置 `server.servlet.session.cookie.same-site=lax`
- 前端：纯原生 HTML5 + CSS3 + ES6 JavaScript（`fetch` 调用后端 REST API），第一版不引入任何第三方前端库或构建工具；题面使用纯文本（`white-space: pre-wrap`）展示，代码编辑器使用原生 `<textarea>`
- 前端静态文件放在 `src/main/resources/static/`，页面之间通过普通链接跳转

## 工程约定
- 包结构：`controller / service / repository / entity / dto / judge / config`
- 统一 REST 返回格式：`{code, message, data}`；统一由 `@RestControllerAdvice` 处理异常
- 每完成一个模块，必须编写对应单元测试并运行 `mvn test`，通过后再结束任务
- 不要一次改动无关模块；不确定的需求先列出假设再动手

## 代码风格与反过度设计约定（严格遵守）
1. **只在系统边界做校验，信任内部代码**
   - 后端参数校验统一在 Controller 层通过 Bean Validation 注解完成（如 `@NotBlank`、`@Size`、`@Min`），语言等固定选项使用枚举做白名单校验；前端仅做基础提示性校验。
   - 进入 Service 及内部私有方法后默认入参已合法，禁止在各层重复编写 `if (x == null)`、`StringUtils.hasText` 或 `Optional.ofNullable(...).orElse(...)` 做冗余兜底。
2. **Fail-Fast，禁止防御性 try-catch**
   - 业务错误直接抛出自定义 `BizException`，系统错误直接向上抛出，统一交给 `@RestControllerAdvice`。
   - 禁止在 Controller / Service 中随意写 `try-catch (Exception e)` 吞异常或返回 `null`/空集合。
   - **允许使用 `try-catch` / `finally` 的唯一三类场景**：
     1. 必须处理的受检异常或资源释放（如在 `finally` 中递归清理判题临时目录）；
     2. 判题执行层将进程/容器超时、IO 错误等转换为判题状态（TLE / RE / CE）；
     3. **异步判题线程的顶层兜底捕获**（线程池内抛出的异常无法到达 `@RestControllerAdvice`，必须在工作线程最外层捕获并将提交状态置为 `SE`，防止任务永久停留在 `JUDGING`）。
3. **拒绝无意义的样板抽象**
   - Entity 使用普通 Java 类（可用 Lombok，满足 JPA 无参构造和可变字段要求）；DTO 统一使用 Java 17 `record`。
   - 单一实现的 Service 直接写具体类（如 `ProblemService`），禁止创建 `XxxService` 接口 + `XxxServiceImpl`；仅 `Language`、`Sandbox` 这类明确存在多实现的策略才定义接口。
   - 禁止编写无意义的 Converter/Mapper 工具类，禁止提前写未用到的占位类、枚举值、备用分支或死代码（YAGNI）。
4. **前端原生 JS 保持直白**
   - 抽出公共 `api.js` 封装 `request(url, options)` 统一处理 `fetch`、401 跳转与错误提示；页面逻辑直接消费响应数据，禁止到处堆砌 `res?.data?.items || []` 过度防御链。
   - 当前页面确定存在的 DOM 节点直接操作，禁止对每个 `getElementById` 都套 `if (!el) return;`。
   - 展示用户输入、题面、代码和报错信息时统一使用 `textContent`，禁止拼装 `innerHTML`。
5. **扁平控制流与有效注释**
   - 优先使用早返回（Early Return）减少嵌套，避免超过 3 层缩进。
   - 中文注释只解释“为什么”（如防管道死锁、判定顺序原因），禁止写复述代码行为的废话注释（如 `// 根据 ID 查询题目`）。

## 判题核心约定
- 判题状态：`PENDING` / `JUDGING` / `AC` / `WA` / `TLE` / `MLE` / `RE` / `CE` / `SE`（系统错误）
- 判题异步执行（固定大小线程池 + 队列），提交后立即返回 `submissionId`，前端轮询结果
- 用户代码在独立临时目录中编译和运行，结束后必须在 `finally` 中彻底清理