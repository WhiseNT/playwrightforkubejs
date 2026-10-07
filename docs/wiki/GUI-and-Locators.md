# GUI 与 Locator

简体中文 | [English](GUI-and-Locators.en.md)

[← Wiki 首页](Home.md) · [API 参考](API-Reference.md) · [快速开始](Quick-Start.md) · [排障](Troubleshooting.md)

## 查询先于输入

```js
page.gui().snapshot().then(function (gui) {
  console.info("screen=" + gui.get("type") + ", open=" + gui.get("open"))
  var widgets = gui.get("widgets")
  for (var i = 0; i < widgets.size(); i++) {
    var item = widgets.get(i)
    console.info(item.get("id") + " " + item.get("role") +
      " translation=" + item.get("translationKey") + " text=" + item.get("text"))
  }
})
```

每次 GUI locator 查询都会重新读取当前 screen 树。对 screen 变化前读到的 widget path 不要跨屏幕复用。

## Selector 语法

| Selector | 何时用 | 匹配语义 |
| --- | --- | --- |
| `translation=menu.singleplayer` | vanilla/KubeJS 控件有 translation key | 按 key 匹配，不依赖 UI 语言；组合文本也可能包含多个 key |
| `gui-text=单人游戏` | 文本固定且语言已知 | 精确匹配显示文本，不做 substring 模糊搜索 |
| `role=button` | 需要按控件角色筛选 | 可选角色：button、textbox、tab、world、list、group、widget |
| `role=button|name=...` | 同屏有多个按钮时缩小范围 | name 是 URL 编码的显示文本；优先使用 `getByRole(role,name)` 自动编码 |
| `widget=root/0/2` | snapshot 已测出唯一 path | 与当前 screen 控件树结构绑定，跨版本/语言可能变化 |
| `world=save-folder` | Select World 列表 | 精确 save ID，或唯一匹配的显示世界名 |

推荐写法：

```js
var returnButton = page.gui().getByTranslation("menu.returnToGame")
returnButton.waitForEnabled(5000)
  .then(function () { return returnButton.click() })
```

`page.gui().getByText(text)` 是 GUI 精确文本 locator；`page.getByText(text)` 保留 legacy `text=` chat-history 语义。需要 GUI 时优先使用 `gui().getByText`、`getByTranslation` 或明确 selector，避免把聊天文本当控件文本。

## 可靠交互规则

1. 先等目标 screen：`page.gui().waitFor("PauseScreen", 10000)`。
2. 按 translation key 或 role/name 定位，不用不稳定的数组下标猜菜单按钮。
3. 若同屏匹配数应当唯一，可用 `.count()` 断言数量，再点。
4. 需要输入的控件先确认 role 是 `textbox`，然后 locator `.fill(text)`。
5. click/fill 后重新 snapshot 或等待下一个 screen/游戏状态，验证真实结果。

Locator 的 `click()` 对 0 个/多个匹配会显式失败；隐藏或禁用控件也不是 actionable。`fill()` 仅接受实际 GUI EditBox；输入如果被过滤或截断会失败，而不是报告假成功。

## 坐标与语言

screen snapshot 的边界坐标采用 `gui-scaled` 坐标系，不是物理窗口像素。优先用 locator 而非绝对坐标。对游戏多语言，翻译键通常比显示文本稳健；自定义模组屏幕要先检查它是否确实暴露了可查询的 vanilla GUI widget 树。

## Screen class 名称

`gui().waitFor(screenType, timeoutMs)` 按真实 screen class 简名/类名等待。例如在本项目历史 Minecraft 1.20.1 客户端验收里，原版箱子实际暴露为通用 `ContainerScreen`（标题为 Chest），不是名为 `ChestScreen` 的类。写测试前先 snapshot 核对目标版本真实 screen type，不要根据屏幕标题臆测 Java 类名。
