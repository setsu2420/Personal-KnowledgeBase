# OCR/VLM 表格转 Markdown 修复设计

## 问题描述

用户上传新资料（含表格图片）时，OCR 请求失败。需要确保表格能正确转化为 Markdown。

## 根因分析

### 当前处理链路

`UploadController` → 异步 `DocumentParseService.parseAndExtract()` → `ImageService.processTableImage()` → `ocrTableImage()`

`ocrTableImage()` 调用链：
1. `getOcrConfig()` 获取配置（优先级：`settings.ocrConfig` → `llm_configs(purpose=ocr)` → `llm_configs(purpose=vlm)` → `chat配置`）
2. `llmService.chatWithVision(config, ...)` 发送图片到 VLM API
3. 清理返回的 Markdown / 转换 HTML 表格

### 失败原因

1. **配置回退到 chat 模型**：无独立 OCR/VLM 配置时，回退到 chat 配置，而 chat 模型通常不支持 vision API
2. **settings.ocrConfig 未初始化**：首次使用时表中无记录
3. **VLM API 调用失败**：密钥无效、模型不支持 vision、或配额用尽
4. **无本地后备**：VLM 失败后没有本地 OCR 回退方案

## 选定方案：VLM-first + PaddleOCR-VL 本地后备

### 变更 1：修复 OCR 配置回退逻辑

**文件**：`ImageService.java` → `getOcrConfig()`

改进：
1. 优先 `settings.ocrConfig`
2. 其次 `llm_configs(purpose=ocr)`
3. 其次 `llm_configs(purpose=vlm)`
4. **新增**：检查 chat 配置是否支持 vision（通过 provider/model 名称判断，如包含 `VL`、`Vision`、`gemini`、`claude` 等关键词）
5. **新增**：如果 chat 也不支持 vision，返回 `null` 而不是不支持 vision 的配置（让下游触发 PaddleOCR-VL 回退）

### 变更 2：添加 PaddleOCR-VL 本地端点

**文件**：`parser-service/parse_document.py`

新增端点 `POST /api/ocr-table`：
- 接收 multipart 图片或 base64 JSON
- 检测 `paddleocr_vl` 模块是否可用
- 使用 PaddleOCR-VL-1.5 识别表格
- 输出 Markdown 表格格式
- 未安装时返回 503 + 安装提示

新增依赖检测（启动时）：
```python
try:
    from paddleocr_vl import ...
    _paddleocr_vl_available = True
except ImportError:
    _paddleocr_vl_available = False
```

### 变更 3：Java 后端添加 PaddleOCR-VL 回退

**文件**：`ImageService.java` → `ocrTableImage()`

修改逻辑：
1. 尝试 VLM API（当前逻辑，getOcrConfig + chatWithVision）
2. **新增**：VLM 失败时，调用 parser-service 的 `POST /api/ocr-table`
3. 两者都失败时，返回明确错误：`[OCR失败：VLM API 不可用且本地 PaddleOCR-VL 未启动。请配置 VLM 模型或启动 parser-service]`

新增方法 `ocrTableImageLocal(byte[] imageData, String mimeType)`：
- HTTP POST 到 `http://localhost:8100/api/ocr-table`
- 超时 120 秒（本地模型较慢）
- parser-service URL 从 `@Value("${parser.service.url:http://localhost:8100}")` 读取

### 变更 4：添加 parser-service 配置

**文件**：`application.properties`

新增配置项：
```properties
parser.service.url=http://localhost:8100
```

### 变更 5：改善错误提示和日志

**文件**：`ImageService.java`

- VLM 配置获取失败时记录 WARN 日志并说明原因
- VLM API 调用失败时记录具体 HTTP 状态码和错误信息
- PaddleOCR-VL 回退时记录 INFO 日志
- 最终失败时返回可操作的错误信息

### 变更 6：更新 parser-service requirements.txt

**文件**：`parser-service/requirements.txt`

添加可选依赖注释：
```
# paddleocr-vl  # 可选：本地表格 OCR（pip install paddleocr-vl）
```

## 不涉及的变更

- 前端 UI 不变
- 数据库 schema 不变
- MinerU 远程 API 逻辑不变
- 非表格图片（`processStandaloneImage`）的处理逻辑不变
- `generateCaption()` 不变（仍用 VLM）

## 文件变更清单

| 文件 | 变更类型 |
|------|---------|
| `backend-springboot/.../service/ImageService.java` | 修改（配置回退 + PaddleOCR-VL 回退） |
| `backend-springboot/.../resources/application.properties` | 修改（添加 parser.service.url） |
| `parser-service/parse_document.py` | 修改（添加 /api/ocr-table 端点） |
| `parser-service/requirements.txt` | 修改（添加可选依赖注释） |
