# 💻Java Socket HTTP 客户端 / 服务器项目

基于 Java 原生 Socket API 开发的轻量级 HTTP 客户端与服务器，支持 HTTP 基础请求响应、长连接、状态码处理及注册登录功能，无任何第三方框架依赖，纯原生实现。

## 项目介绍
😿


## 技术栈

- 编程语言：Java 8+
- 核心技术：Java Socket API（BIO 模型）、HTTP/1.1 协议
- 开发工具：IntelliJ IDEA、Git、Postman（接口测试）
- 代码托管：GitHub

## 功能清单

### 1. 服务器端功能



### 2. 客户端功能



## 快速开始

### 1. 环境准备

- JDK 8 及以上
- Git（代码拉取）
- 任意 Java 开发工具（IDEA 推荐）

### 2. 代码拉取

```bash
git clone https://github.com/594mzy/HttpProject.git
cd HttpProject
```

### 3. 项目结构

plaintext

```plaintext
HttpProject
├─ src
│  ├─ com.http.common
│  │  ├─ Request.java
│  │  ├─ Response.java
│  │  ├─ ParamParser.java
│  │  ├─ UrlParser.java
│  │  └─ MimeUtil.java
│  ├─ com.http.server
│  │  ├─ HttpServer.java
│  │  ├─ ClientHandler.java
│  │  ├─ RequestParser.java
│  │  ├─ ResponseUtil.java
│  │  ├─ ResourceHandler.java
│  │  └─ UserHandler.java
│  ├─ com.http.client
│  │  ├─ HttpClient.java
│  │  ├─ ResponseParser.java
│  │  └─ RedirectHandler.java
│  ├─ com.http.test
│  │  └─ ApiTest.java
│  └─ resources
│     └─ static
│        ├─ index.html
│        ├─ test.txt
│        └─ test.png
└─ README.md
```

## 开发团队

- 项目创建者：[594mzy](https://github.com/594mzy)
- 协作开发者：
