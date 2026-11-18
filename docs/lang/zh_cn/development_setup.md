# 开发环境说明

这份文档用于存放本地开发说明，不适合直接放在公开仓库首页。

## JDK

Gradle 和 Cleanroom 客户端使用 Java 25。开发和游戏验收的 Cleanroom 版本上限为
`0.6.13-alpha`，依赖和实现须兼容该版本。Gradle 的 Unimined 开发加载器当前固定为
`0.5.6-alpha`。Forge 服务端兼容包的字节码目标为 Java 8。

如果系统默认仍然指向 Java 8，可以使用仓库中已有的辅助脚本：

```bat
scripts\gradle-jdk25.cmd classes
scripts\gradle-jdk25.cmd runClient
```

## VSCode

仓库中包含 `.vscode` 配置，主要用于本地开发便利性。

当前配置下的常用快捷方式：

- `F5` 启动本地 Ponder 客户端配置
- `Ctrl+Shift+B` 运行配置好的构建任务
- `Terminal > Run Task` 从工作区任务菜单运行客户端或服务端任务

这些文件只是可选编辑器配置，不属于 mod 的公共 API，也不属于运行时功能的一部分。

## 说明

- `run/` 是本地开发状态和示例目录
- `build/` 和 `.gradle/` 是构建产物 / 缓存
- 临时验证目录不应视为项目正式内容
