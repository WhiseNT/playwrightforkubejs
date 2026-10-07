# Playwright For KubeJS Wiki

简体中文 | [English](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en)

欢迎！本 Wiki 面向第一次使用的整合包玩家、KubeJS 脚本作者、模组开发者和编码助手。

> **快速认识项目：**这是当前 `mc-1.21.1` 分支的 Minecraft 1.21.1 / NeoForge 客户端侧的 KubeJS 自动化与测试 API。它提供 GUI、locator、玩家状态、背包/菜单槽位、输入和有界异步任务。它不是浏览器自动化工具、不是官方 Playwright，也没有完整 A* 路径规划。

## 按你的目标开始

| 你想做什么 | 先读 |
| --- | --- |
| 安装 mod 并运行第一个测试 | [安装与兼容版本](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation) → [新手实操教程](../tutorial/Getting-Started.md) |
| 没有 Gradle 环境，由 Agent 启动已有 Minecraft 实例 | [NTLauncher CLI 可选工作流](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing) |
| 五分钟理解任务链 | [快速开始](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start) |
| 找 GUI 按钮、文本框、控件 | [GUI 与 Locator](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators) |
| 读背包、箱子、工作台容器槽位 | [背包与容器](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers) |
| 处理等待、失败、取消、reload | [异步任务与错误](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors) |
| 运行真实客户端验收套件 | [E2E 测试](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing) |
| 让 AI 帮忙写脚本但不虚构 API | [AI 编码指南](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide) |
| 脚本失败或 locator 匹配不到 | [故障排查](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting) |
| 查模块方法的简明索引 | [API 参考](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference) |

## 适用版本和证据边界

当前发布目标为 `0.1.0+mc1.21.1`（tag `v0.1.0-mc1.21.1`）：Java 21、Minecraft 1.21.1、NeoForge 21.1.256、KubeJS `2101.7.2-build.379`，Rhino 按 KubeJS 依赖要求安装；Architectury 不再是本分支明确依赖。历史 `v0.1.0` 仍适用于 Forge 1.20.1，不能混用 JAR。发布说明以 JAR 所属 tag 的 README 为准。

2026 年 10 月 7 日当前分支英文/简体中文客户端均跑通过。最新单次英文 `diag-knockback-fix` 结果为 64 个 required 阶段 PASS、零校验错误、107 秒；尚无可靠性统计。详情见 [E2E 测试](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing)。公共 API 尽量保持跨版本方法名、参数、返回值和错误语义；原生命令/NBT/Java/KubeJS 特性可不同，跨版本统一测试夹具仍未实现。

当前真实客户端覆盖英文/简体中文 UI、菜单、背包与真实工作台合成、原版箱子槽转移/关闭重开/存档重进、装备、战斗、有限移动、重载和输入释放。测试采用单人平坦世界及命令准备固定夹具。自定义模组屏幕、其他 loader/版本、多人和真实整合包组合仍需要单独验收。

## 站点导航

- [安装](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation) · [快速开始](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start) · [API 索引](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference)
- [GUI 定位](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators) · [容器](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers) · [异步](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors)
- [测试套件](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing) · [AI 指南](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide) · [排障](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting)
- [新手逐步实操](../tutorial/Getting-Started.md) · [仓库 README](../../README.md)
