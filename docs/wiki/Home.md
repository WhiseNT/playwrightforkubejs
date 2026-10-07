# Playwright For KubeJS Wiki

欢迎！本 Wiki 面向第一次使用的整合包玩家、KubeJS 脚本作者、模组开发者和编码助手。

> **快速认识项目：**这是 Minecraft 1.20.1 / Forge 客户端侧的 KubeJS 自动化与测试 API。它提供 GUI、locator、玩家状态、背包/菜单槽位、输入和有界异步任务。它不是浏览器自动化工具、不是官方 Playwright，也没有完整 A* 路径规划。

## 按你的目标开始

| 你想做什么 | 先读 |
| --- | --- |
| 安装 mod 并运行第一个测试 | [安装与兼容版本](Installation.md) → [新手实操教程](../tutorial/Getting-Started.md) |
| 没有 Gradle 环境，由 Agent 启动已有 Minecraft 实例 | [NTLauncher CLI 可选工作流](E2E-Testing.md) |
| 五分钟理解任务链 | [快速开始](Quick-Start.md) |
| 找 GUI 按钮、文本框、控件 | [GUI 与 Locator](GUI-and-Locators.md) |
| 读背包、箱子、工作台容器槽位 | [背包与容器](Inventory-and-Containers.md) |
| 处理等待、失败、取消、reload | [异步任务与错误](Async-Tasks-and-Errors.md) |
| 运行真实客户端验收套件 | [E2E 测试](E2E-Testing.md) |
| 让 AI 帮忙写脚本但不虚构 API | [AI 编码指南](AI-Coding-Guide.md) |
| 脚本失败或 locator 匹配不到 | [故障排查](Troubleshooting.md) |
| 查模块方法的简明索引 | [API 参考](API-Reference.md) |

## 适用版本和证据边界

项目发布配置和真实客户端验收目标为 Java 17、Minecraft 1.20.1、Forge 47.2.0、KubeJS Forge `2001.6.5-build.26`、Rhino `2001.2.2-build.17`、Architectury `9.2.14`。发布说明以 JAR 所属 tag 的 README 为准。

当前真实客户端覆盖英文/简体中文 UI、菜单、背包与真实工作台合成、原版箱子槽转移/关闭重开/存档重进、装备、战斗、有限移动、重载和输入释放。测试采用单人平坦世界及命令准备固定夹具。自定义模组屏幕、其他 loader/版本、多人和真实整合包组合仍需要单独验收。

## 站点导航

- [安装](Installation.md) · [快速开始](Quick-Start.md) · [API 索引](API-Reference.md)
- [GUI 定位](GUI-and-Locators.md) · [容器](Inventory-and-Containers.md) · [异步](Async-Tasks-and-Errors.md)
- [测试套件](E2E-Testing.md) · [AI 指南](AI-Coding-Guide.md) · [排障](Troubleshooting.md)
- [新手逐步实操](../tutorial/Getting-Started.md) · [仓库 README](../../README.md)
