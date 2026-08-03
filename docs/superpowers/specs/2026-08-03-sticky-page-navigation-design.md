# 页面顶部导航吸顶修复设计

## 背景与根因

“记一笔”页面的 Vant 顶部导航已经声明 `position: sticky; top: 0`，但页面根容器 `.page` 使用了 `overflow-x: hidden`。浏览器会把其纵向溢出计算为 `auto`，从而把 `.page` 识别为滚动容器；实际发生滚动的却是窗口，因此顶部导航不会相对窗口吸顶。

在 430 × 932 视口复现时，窗口滚动 188px 后导航顶部从 0px 移至 -188px，而 `.page.scrollTop` 仍为 0。相同的横向溢出写法也存在于移动端后台布局，并会影响其顶部导航和顶部标签。

## 目标

- 页面上下滚动时，“记一笔”顶部导航始终停留在视口顶部。
- 统一修复所有复用 `.page` 与全局 `.van-nav-bar` 的普通页面。
- 同步修复后台移动端布局中相同的吸顶失效模式。
- 保持现有视觉、文档流占位、横向内容裁剪和桌面端页面框架不变。

## 方案

保留现有 `position: sticky`，将仅用于横向裁剪的 `overflow-x: hidden` 改为 `overflow-x: clip`。`clip` 能继续阻止横向内容溢出，但不会创建新的滚动容器，因此吸顶元素会重新以窗口为滚动参照。

修改范围：

- `frontend/src/styles/main.css`
  - `.page` 使用 `overflow-x: clip`。
  - 桌面断点不再用 `overflow: hidden auto` 重新创建滚动容器，继续使用不建立滚动容器的裁剪方式。
- `frontend/src/views/admin/AdminLayoutView.vue`
  - `.admin-shell` 与 `.admin-main` 的横向裁剪改为 `clip`，使移动端后台导航和标签栏正常吸顶。
- 前端回归检查
  - 增加并接入 `npm run check:ui` 的静态回归检查，防止上述结构容器重新使用会破坏吸顶行为的 `overflow-x: hidden`。

本次不改为 `position: fixed`，因为固定定位会脱离文档流，需要在所有页面补偿导航高度，并增加内容遮挡、安全区和桌面最大宽度的回归风险。也不在本次迁移页面头组件，避免扩大为无关重构。

## 验证

1. 先运行新增回归检查并确认其在现有样式下按预期失败。
2. 完成最小样式修改后确认新增检查通过。
3. 运行 `npm run check:ui` 与 `npm run build`。
4. 在 430 × 932 视口打开 `/quick-add?type=EXPENSE`，滚动页面后验证导航的 `getBoundingClientRect().top` 保持为 0，并确认窗口滚动正常、页面未产生独立纵向滚动。
5. 检查同类普通页面与后台移动端顶栏不存在遮挡或横向溢出回归。
