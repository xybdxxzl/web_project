# Mini OJ

Mini OJ 是一个使用 Spring Boot、JPA 和原生 HTML/CSS/JavaScript 的在线判题系统。当前支持 Java 和 C++17 提交、异步判题、题库管理和个人提交历史。

## 环境依赖

| 依赖 | 用途 |
| --- | --- |
| JDK 17 | 构建、启动应用，以及在 Process 模式下编译和运行提交。确认 `java -version` 和 `mvn -version` 指向 JDK。 |
| Maven 3.6.3 或更高 | 下载依赖、运行测试和启动应用。首次构建需要访问 Maven 仓库。 |
| Docker Engine 或 Docker Desktop | 默认 Docker 判题和容器专项测试需要可用的 Linux 容器守护进程。 |
| MySQL 8.x（可选） | 启用 `mysql` 配置时使用。默认使用项目目录下的 H2 文件数据库。 |

## 本地启动

以下命令在项目根目录运行。首次初始化数据库会写入 A+B 及样例、隐藏用例。

### Windows 一键启动外置 Tomcat

装好 JDK 17 或更高版本后，双击 `start-tomcat.cmd`。脚本构建 `target/mini-oj.war` 并部署到外置 Tomcat。首页可访问后，脚本会用默认浏览器打开 [http://127.0.0.1:8082/](http://127.0.0.1:8082/)。按 Ctrl+C 停止。

脚本使用 `MAVEN_HOME` 或 PATH 中的 Maven，并使用 `CATALINA_HOME` 指定的 Tomcat 10.1。未设置时，缺少的工具由脚本从 Apache 下载，经 SHA-512 校验后保存在 `.tomcat-local/`。首次构建还需要访问 Maven 仓库。已有 Tomcat 9 可以继续供其他项目使用。

双击启动仅监听 `127.0.0.1`，未设置沙箱时采用 Process 模式，供可信代码在本机验收。可在 PowerShell 中指定端口、Docker 沙箱或已有工具目录。

```powershell
.\start-tomcat.cmd -Port 8081
.\start-tomcat.cmd -SandboxType docker
.\start-tomcat.cmd -TomcatHome 'D:\tools\apache-tomcat-10.1.62' -MavenHome 'D:\tools\apache-maven-3.9.16'
```

外置 Tomcat 的端口由 `-Port` 控制。已有 MySQL 或管理员环境变量会传给应用。手动部署时，将 `target/mini-oj.war` 作为 `ROOT.war` 放入 Tomcat 10.1 的 `webapps`，项目页面和 API 使用根路径。

### H2 和 Docker（默认）

```powershell
docker info
docker pull eclipse-temurin:17
docker pull gcc:13
mvn spring-boot:run
```

通过 Maven 或 IDEA 运行 `MiniOjApplication` 后，访问 [http://localhost:8082](http://localhost:8082)。内嵌 Tomcat 和一键启动的外置 Tomcat 共用默认端口，任选一种启动方式。H2 数据文件位于 `data/minioj.mv.db`。如需首次创建管理员，启动前通过环境变量 `MINIOJ_ADMIN_PASSWORD` 设置 6–32 位密码，账号为 `admin`。后续修改该变量不会更改已创建的密码。

### 切换到 Process 沙箱

```powershell
$env:OJ_SANDBOX_TYPE = 'process'
mvn spring-boot:run
```

Process 直接以应用的宿主机权限运行编译器和程序，仅用于本地验证可信代码。本地 C++ 需要 PATH 中的 `g++` 支持 `-std=c++17`。Linux/macOS 运行 C++ 时使用 `ulimit -v` 设置虚拟内存上限。Windows 的 Process 模式无法可靠限制 C++ 内存，验收内存判定请使用 Docker。恢复 Docker 时执行 `Remove-Item Env:OJ_SANDBOX_TYPE`，或赋值为 `docker`。

Docker 按语言选择镜像，Java 使用 `eclipse-temurin:17`，C++ 使用 `gcc:13`，编译和运行均在容器内。C++ 运行容器使用题目的内存上限，编译容器至少分配 256 MiB。运行启动余量 `oj.sandbox.docker.startup-margin-ms` 默认为 1000 ms，编译限时 `oj.judge.compile-timeout-ms` 默认为 10000 ms。

### 切换到 MySQL

先创建数据库并配置有该数据库权限的账号。首次启动需要建表权限，应用使用 `schema-mysql.sql` 初始化结构。

```sql
CREATE DATABASE minioj CHARACTER SET utf8mb4;
```

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:MINIOJ_DB_URL = 'jdbc:mysql://127.0.0.1:3306/minioj'
$env:MINIOJ_DB_USER = 'minioj'
$env:MINIOJ_DB_PASSWORD = Read-Host 'MySQL 密码' -MaskInput
mvn spring-boot:run
```

`-MaskInput` 适用于 PowerShell 7。H2 与 MySQL 各自保存数据，切换配置不会迁移已有记录。移除 `SPRING_PROFILES_ACTIVE` 即恢复 H2。数据库与沙箱可以分别切换。同一个数据库应只连接一个判题应用实例，因为应用重启时会把未完成的提交标为 `SE`。

## 测试与验收

请在未启用 `mysql` 配置的终端执行测试，测试应用使用独立的 H2 数据库。

```powershell
mvn test
mvn test "-Dtest=SubmissionFlowTest"
docker pull eclipse-temurin:17
docker pull gcc:13
mvn test "-Dtest=DockerSandboxTest"
```

- `SubmissionFlowTest` 在随机端口启动应用，使用隔离的内存 H2 和 Process 沙箱，验证注册、登录、查找 A+B、提交、轮询获得 AC，以及在历史记录中读取结果。
- `DockerSandboxTest` 验证容器参数、超时清理、内存超限、断网和运行目录只读。Linux 容器守护进程不可用时，只有依赖守护进程的测试会跳过。
- 手动验收时，在首页注册并登录，进入 A+B，提交下方代码，等待 AC。打开「我的提交」，点击对应编号核对源码和耗时。

```java
public class Main {
    public static void main(String[] args) {
        var input = new java.util.Scanner(System.in);
        System.out.println(input.nextLong() + input.nextLong());
    }
}
```

Docker 判题超时会由应用强制移除容器，运行中的容器内部也设有更长的兜底期限。若宿主机强制终止应用，可能留下 `mini-oj-*` 临时目录或尚未启动的容器。可先用 `docker ps -a --filter name=mini-oj-` 检查，再确认相关容器和进程已停止。

## 目录结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java/com/minioj/controller` | REST 接口和统一异常处理 |
| `src/main/java/com/minioj/service` | 账号、题目和提交业务 |
| `src/main/java/com/minioj/entity`、`repository`、`dto` | 持久化实体、数据库查询和请求响应 |
| `src/main/java/com/minioj/judge` | 判题队列、语言策略、Process 和 Docker 沙箱 |
| `src/main/java/com/minioj/config` | 会话鉴权、管理员和示例题初始化 |
| `src/main/resources` | 应用配置、两种数据库的建表脚本与 `static/` 前端 |
| `src/test/java/com/minioj` | 控制器、判题器、Docker 和端到端测试 |

## 核心 REST API

响应采用 `{code, message, data}`。登录状态通过会话 Cookie 维护。列表页码从 1 开始，`size` 默认 20。

| 方法与路径 | 权限与用途 |
| --- | --- |
| `POST /api/auth/register` | 公开。提交 `username`、`password` 创建普通用户 |
| `POST /api/auth/login` | 公开。提交 `username`、`password` 建立会话 |
| `GET /api/auth/me` | 登录。查看当前用户 |
| `POST /api/auth/logout` | 登录。以 `application/json` 发送 `{}` 并结束会话 |
| `GET /api/problems` | 公开。分页查询题目，可传 `difficulty` 和 `keyword` |
| `GET /api/problems/{id}` | 公开。查看题目与公开样例 |
| `GET /api/admin/problems` | 管理员。分页查询题目 |
| `GET /api/admin/problems/{id}` | 管理员。查看题目与全部用例 |
| `POST /api/admin/problems` | 管理员。创建题目和用例 |
| `PUT /api/admin/problems/{id}` | 管理员。修改题目并更新用例 |
| `DELETE /api/admin/problems/{id}` | 管理员。删除题目和用例 |
| `POST /api/submissions` | 登录。提交 `problemId`、`language`（`JAVA` 或 `CPP`）、`code`，接收提交编号 |
| `GET /api/submissions` | 登录。分页查看本人提交，按时间倒序 |
| `GET /api/submissions/{id}` | 本人或管理员。查看判题状态、耗时、源码和错误信息 |
