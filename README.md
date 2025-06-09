<hr/>
    Project-Streaming-Control-Platform为企业级分布式系统设计的高性能流量控制与治理平台，基于 SpringCloud Alibaba 微服务架构实现，融合服务网关、分布式事务、异步通信、限流熔断等技术，保障系统在高并发场景下的稳定性、安全性与高可用性。

### 项目亮点

- **热门技术**：采用时下企业最热门的技术框架，如 SpringCloud-Gateway、Nacos、Sentinel 等，主打一个硬核，与真实的企业项目接轨。
- **单体与微服务**：提供单体和微服务两个版本，拥有从单体到微服务架构的改造全过程，并深入理解两种架构的优缺点。
- **持续集成**：提供持续集成和持续部署的完整配置，带你从 0-1 用 Docker 上线 生产环境级别的真实项目。


## 项目简介

Project-Streaming-Control-Platform 包括认证、流程、项目管理、用户、网关等服务。包含了 Redis 缓存、RocketMQ 消息队列、Docker 容器化、Jenkins 自动化部署、Spring Security 安全框架、Nacos 服务注册和发现、Sentinel 熔断限流、Seata 分布式事务、Spring Boot Actuator 服务监控、SkyWalking 链路追踪、OpenFeign 服务调用，Vue3 前端框架等互联网开发中需要用到的主流技术栈。

### 代码结构

```
com.laigeoffer.Project-Streaming-Control-Platform     
├── Project-Streaming-Control-Platform-ui              // 前端框架 [1024]
├── Project-Streaming-Control-Platform-gateway         // 网关模块 [6880]
├── Project-Streaming-Control-Platform-auth            // 认证中心 [6800]
├── Project-Streaming-Control-Platform-api             // 接口模块
│       └── Project-Streaming-Control-Platform-api-system                          // 系统接口
│       └── Project-Streaming-Control-Platform-api-workflow                        // 流程接口
├── Project-Streaming-Control-Platform-base          // 通用模块
│       └── Project-Streaming-Control-Platform-base-core                           // 核心模块组件
│       └── Project-Streaming-Control-Platform-base-datasource                     // 多数据源组件
│       └── Project-Streaming-Control-Platform-base-seata                          // 分布式事务组件
│       └── Project-Streaming-Control-Platform-base-security                       // 安全模块组件
│       └── Project-Streaming-Control-Platform-base-swagger                        // 系统接口组件
│       └── Project-Streaming-Control-Platform-base-notice                         // 消息组件组件
├── Project-Streaming-Control-Platform-modules         // 业务模块
│       └── Project-Streaming-Control-Platform-system                              // 系统模块 [6801]
│       └── Project-Streaming-Control-Platform-gen                                 // 代码生成 [6802]
│       └── Project-Streaming-Control-Platform-job                                 // 定时任务 [6803]
│       └── Project-Streaming-Control-Platform-project                             // 项目服务 [6806]
│       └── Project-Streaming-Control-Platform-workflow                            // 流程服务 [6808]
├── Project-Streaming-Control-Platform-monitor             						  // 监控中心 [6888]                 
```

### 环境搭建

### 开发工具

|        工具        | 说明           | 官网                                                                                                                       | 
|:----------------:|--------------|--------------------------------------------------------------------------------------------------------------------------|
|       IDEA       | java开发工具     | [https://www.jetbrains.com](https://www.jetbrains.com)                                                                   |
|   visualstudio   | web开发工具      | [https://code.visualstudio.com/](https://code.visualstudio.com/)                                                         |
|      Chrome      | 浏览器          | [https://www.google.com/intl/zh-CN/chrome](https://www.google.com/intl/zh-CN/chrome)                                     |
|   ScreenToGif    | gif录屏        | [https://www.screentogif.com](https://www.screentogif.com)                                                               |
|     SniPaste     | 截图           | [https://www.snipaste.com](https://www.snipaste.com)                                                                     |
|     PicPick      | 图片处理工具       | [https://picpick.app](https://picpick.app)                                                                               |
|     MarkText     | markdown编辑器  | [https://github.com/marktext/marktext](https://github.com/marktext/marktext)                                             |
|       curl       | http终端请求     | [https://curl.se](https://curl.se)                                                                                       |
|     Postman      | API接口调试      | [https://www.postman.com](https://www.postman.com)                                                                       |
|     draw.io      | 流程图、架构图绘制    | [https://www.diagrams.net/](https://www.diagrams.net/)                                                                   |
|      Axure       | 原型图设计工具      | [https://www.axure.com](https://www.axure.com)                                                                           |
|     navicat      | 数据库连接工具      | [https://www.navicat.com](https://www.navicat.com)                                                                       |
|     DBeaver      | 免费开源的数据库连接工具 | [https://dbeaver.io](https://dbeaver.io)                                                                                 |
|      iTerm2      | mac终端        | [https://iterm2.com](https://iterm2.com)                                                                                 |
| windows terminal | win终端        | [https://learn.microsoft.com/en-us/windows/terminal/install](https://learn.microsoft.com/en-us/windows/terminal/install) |
|   SwitchHosts    | host管理       | [https://github.com/oldj/SwitchHosts/releases](https://github.com/oldj/SwitchHosts/releases)                             |


### 开发环境

|      工具       | 版本        | 下载                                                                                                                     |
|:-------------:|:----------|------------------------------------------------------------------------------------------------------------------------|
|      jdk      | 1.8+      | [https://www.oracle.com/java/technologies/downloads/#java8](https://www.oracle.com/java/technologies/downloads/#java8) |
|     maven     | 3.4+      | [https://maven.apache.org/](https://maven.apache.org/)                                                                 |
|     mysql     | 5.7+/8.0+ | [https://www.mysql.com/downloads/](https://www.mysql.com/downloads/)                                                   |
|     redis     | 5.0+      | [https://redis.io/download/](https://redis.io/download/)                                                               |
| elasticsearch | 8.0.0+    | [https://www.elastic.co/cn/downloads/elasticsearch](https://www.elastic.co/cn/downloads/elasticsearch)                 |
|     nginx     | 1.10+     | [https://nginx.org/en/download.html](https://nginx.org/en/download.html)                                               |
|   rocketmq    | 5.0.4+    | [https://www.rabbitmq.com/news.html](https://www.rabbitmq.com/news.html)                                               |
|    ali-oss    | 3.15.1    | [https://help.aliyun.com/document_detail/31946.html](https://help.aliyun.com/document_detail/31946.html)               |
|      git      | 2.34.1    | [http://github.com/](http://github.com/)                                                                               |
|    docker     | 4.10.0+   | [https://docs.docker.com/desktop/](https://docs.docker.com/desktop/)                                                   |
|    freessl    | https证书   | [https://freessl.cn/](https://freessl.cn/)                                                                             |

## 内置功能
1.  用户管理：用户是系统操作者，该功能主要完成系统用户配置。
2.  部门管理：配置系统组织机构（公司、部门、小组），树结构展现支持数据权限。
3.  岗位管理：配置系统用户所属担任职务。
4.  菜单管理：配置系统菜单，操作权限，按钮权限标识等。
5.  角色管理：角色菜单权限分配、设置角色按机构进行数据范围权限划分。
6.  字典管理：对系统中经常使用的一些较为固定的数据进行维护。
7.  参数管理：对系统动态配置常用参数。
8.  通知公告：系统通知公告信息发布维护。
9.  操作日志：系统正常操作日志记录和查询；系统异常信息日志记录和查询。
10. 登录日志：系统登录日志记录查询包含登录异常。
11. 在线用户：当前系统中活跃用户状态监控。
12. 定时任务：在线（添加、修改、删除)任务调度包含执行结果日志。
13. 代码生成：前后端代码的生成（java、html、xml、sql）支持CRUD下载 。
14. 系统接口：根据业务代码自动生成相关的api接口文档。
15. 服务监控：监视当前系统CPU、内存、磁盘、堆栈等相关信息。
16. 缓存监控：对系统的缓存信息查询，命令统计等。
17. 在线构建器：拖动表单元素生成相应的HTML代码。
18. 连接池监视：监视当前系统数据库连接池状态，可进行分析SQL找出系统性能瓶颈。
