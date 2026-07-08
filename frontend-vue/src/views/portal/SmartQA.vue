<template>
  <div class="smart-qa">
    <el-row :gutter="16">
      <!-- 左侧：对话历史 -->
      <el-col :span="6">
        <div class="session-panel">
          <div class="panel-header">
            <span>会话历史</span>
            <el-button size="small" @click="newSession">+ 新会话</el-button>
          </div>
          <div class="session-list">
            <div
              v-for="s in sessions"
              :key="s.session_id"
              :class="['session-item', { active: s.session_id === currentSessionId }]"
              @click="loadSession(s.session_id)"
            >
              <span class="session-title">{{ s.title }}</span>
              <div class="session-actions">
                <el-tag size="small" type="info">{{ s.msg_count }}条</el-tag>
                <el-icon class="delete-btn" @click.stop="deleteSession(s.session_id)"><Delete /></el-icon>
              </div>
            </div>
            <el-empty v-if="sessions.length === 0" description="暂无会话" :image-size="60" />
          </div>
        </div>
      </el-col>

      <!-- 右侧：对话区 -->
      <el-col :span="18">
        <div class="chat-panel">
          <div class="chat-header">
            <span class="chat-title">智能问答</span>
            <div class="chat-header-right">
              <el-switch v-model="streamEnabled" size="small" active-text="流式" inactive-text="普通" @change="onStreamToggle" />
              <span class="chat-hint">语义检索 · 多文档交叉验证 · 来源溯源</span>
            </div>
          </div>

          <div class="chat-messages" ref="chatRef">
            <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
              <div :class="msg.role === 'user' ? 'message-bubble user-bubble' : 'message-wrapper'">
                <!-- 用户消息 -->
                <div v-if="msg.role === 'user'" class="bubble-text" v-html="formatText(msg.content, 'user')" />

                <!-- 助手消息 -->
                <template v-else>
                  <!-- 文字回答（表格由 LLM 通过 markdown 自然生成） -->
                  <div class="answer-text markdown-body" v-html="formatText(msg.content, 'assistant')" />

                  <!-- 置信度 -->
                  <div v-if="msg.confidence" class="confidence">
                    置信度: {{ (msg.confidence * 100).toFixed(0) }}%
                  </div>

                  <!-- 引用来源面板（回答结束后显示） -->
                  <div v-if="msg.sources && msg.sources.length > 0" class="references-panel">
                    <div class="references-header" @click="toggleRefs(i)">
                      <span class="references-icon">&#128196;</span>
                      <span class="references-title">引用来源 ({{ msg.sources.length }})</span>
                      <span class="references-toggle">{{ expandedRefs.has(i) ? '收起' : '展开' }}</span>
                    </div>
                    <div class="references-list" :class="{ collapsed: !expandedRefs.has(i) }">
                      <div v-for="(src, j) in (expandedRefs.has(i) ? msg.sources : msg.sources.slice(0, 3))" :key="j" class="reference-item">
                        <span class="ref-index">[{{ src.index || (j + 1) }}]</span>
                        <span :class="['ref-type-badge', 'ref-type-' + (src.media_type || 'text')]">
                          {{ refTypeLabel(src.media_type || 'text') }}
                        </span>
                        <span class="ref-title" :title="src.source_origin || ''">
                          {{ src.title }}
                          <span v-if="src.source_name" style="color: #64748B; font-size: 11px; margin-left: 6px;">
                            (来自: {{ src.source_name }})
                          </span>
                        </span>
                      </div>
                      <div v-if="!expandedRefs.has(i) && msg.sources.length > 3" class="references-more" @click.stop="expandedRefs.add(i)">
                        +{{ msg.sources.length - 3 }} 更多来源...
                      </div>
                    </div>
                  </div>

                  <!-- 图片区域（回答结束后显示） -->
                  <div v-if="msg.images && msg.images.length > 0" class="images-section">
                    <div class="images-header" @click="toggleImages(i)">
                      <span class="images-icon">&#128444;</span>
                      <span class="images-title">相关图片 ({{ msg.images.length }})</span>
                      <span class="images-toggle">{{ expandedImages.has(i) ? '收起' : '展开' }}</span>
                    </div>
                    <div class="images-content" :class="{ collapsed: !expandedImages.has(i) }">
                      <div class="images-grid">
                        <div v-for="(img, ii) in msg.images" :key="'i'+ii" class="image-wrapper" @click="previewImage(img)">
                          <img
                            :src="getImageUrl(img.media_path || '')"
                            class="result-image"
                            :alt="img.title || ''"
                            @error="handleImageError"
                          />
                          <div class="image-error" style="display:none; padding:20px; background:#f5f5f5; text-align:center; color:#999; border-radius:4px;">
                            图片加载失败: {{ img.media_path }}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- 表格面板（回答结束后显示，去重后仅显示未在回答中的表格） -->
                  <div v-if="getFilteredTables(msg).length > 0" class="tables-section">
                    <div class="tables-header" @click="toggleTables(i)">
                      <span class="tables-icon">&#128202;</span>
                      <span class="tables-title">相关表格 ({{ getFilteredTables(msg).length }})</span>
                      <span class="tables-toggle">{{ expandedTables.has(i) ? '收起' : '展开' }}</span>
                    </div>
                    <div class="tables-content" :class="{ collapsed: !expandedTables.has(i) }">
                      <div v-for="(tbl, ti) in getFilteredTables(msg)" :key="'t'+ti" class="table-item">
                        <div class="media-title">{{ tbl.title }}</div>
                        <div class="markdown-table" v-html="renderMarkdownTable(tbl.table_markdown)" />
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
            <el-empty v-if="messages.length === 0" description="输入问题开始对话" />
          </div>

          <div class="chat-input-area">
            <el-input
              v-model="question"
              placeholder="输入您的问题..."
              @keyup.enter="askQuestion"
              :disabled="asking"
              size="large"
            />
            <el-button type="primary" size="large" @click="askQuestion" :loading="asking" :disabled="asking">
              提问
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>
    <!-- 图片预览弹窗 -->
    <el-dialog v-model="previewVisible" width="fit-content" top="5vh" :show-close="true" class="image-preview-dialog" append-to-body>
      <img :src="previewUrl" class="preview-full-image" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { Delete } from '@element-plus/icons-vue'
import { askQuestion, askQuestionStream, getQAChatSessions, getQAChatSession, deleteQAChatSession, getMediaUrl, getSettings, updateSettings } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { marked } from 'marked'

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
})

interface Message {
  role: 'user' | 'assistant'
  content: string
  confidence?: number
  sources?: Array<{ title: string; library: string; source_name: string; source_origin?: string; index?: number; media_type?: string }>
  tables?: Array<{ id: number; title: string; table_markdown: string; source_origin: string; source_name: string; score: number }>
  images?: Array<{ id: number; caption: string; media_path: string; title: string; source_origin: string; source_name: string; score: number }>
  _meta?: any  // 临时存储元数据
}

const messages = ref<Message[]>([])
const question = ref('')
const asking = ref(false)
const sessions = ref<any[]>([])
const currentSessionId = ref('session_' + Date.now())
const chatRef = ref<HTMLElement | null>(null)
const expandedRefs = ref<Set<number>>(new Set())
const expandedTables = ref<Set<number>>(new Set())
const expandedImages = ref<Set<number>>(new Set())
const previewVisible = ref(false)
const previewUrl = ref('')
const streamEnabled = ref(true) // 流式回答开关，默认开启

function formatText(text: string, role: 'user' | 'assistant' = 'assistant'): string {
  if (!text) return ''
  if (role === 'user') {
    return text.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
  // assistant 消息使用 markdown 渲染，表格由 LLM 通过 markdown 自然生成
  return marked.parse(text) as string
}

/** 将markdown表格转为HTML */
function renderMarkdownTable(markdown: string): string {
  if (!markdown) return ''
  return marked.parse(markdown) as string
}

/** 过滤掉已在回答文本中包含的表格（去重） */
function getFilteredTables(msg: Message): Array<any> {
  if (!msg.tables || msg.tables.length === 0) return []
  if (!msg.content) return msg.tables
  const answerText = msg.content.toLowerCase()
  return msg.tables.filter(tbl => {
    // 检查表格标题是否已在回答中出现
    if (tbl.title && answerText.includes(tbl.title.toLowerCase())) return false
    // 检查表格内容的前几个关键词是否已在回答中出现
    if (tbl.table_markdown) {
      // 提取表格第一行的非标题内容作为特征
      const lines = tbl.table_markdown.split('\n').filter((l: string) => l.includes('|')).slice(0, 3)
      const tableText = lines.join(' ').toLowerCase()
      if (tableText.length > 10 && answerText.includes(tableText.substring(0, 30))) return false
    }
    return true
  })
}

/** 从media_path构建可访问的图片URL */
function getImageUrl(mediaPath: string): string {
  const url = getMediaUrl(mediaPath)
  console.log('[SmartQA] getImageUrl:', mediaPath, '->', url)
  return url
}

/** 图片加载失败处理 */
function handleImageError(e: Event) {
  const target = e.target as HTMLImageElement
  console.warn('[SmartQA] 图片加载失败:', target.src)
  target.style.display = 'none'
  const errorDiv = target.nextElementSibling as HTMLElement | null
  if (errorDiv) {
    errorDiv.style.display = 'block'
  }
}

/** 图片预览 */
function previewImage(img: any) {
  const url = getImageUrl(img.media_path)
  if (url) {
    previewUrl.value = url
    previewVisible.value = true
  }
}

/** 切换引用面板展开/折叠 */
function toggleRefs(index: number) {
  if (expandedRefs.value.has(index)) {
    expandedRefs.value.delete(index)
  } else {
    expandedRefs.value.add(index)
  }
}

/** 切换表格区域展开/折叠 */
function toggleTables(index: number) {
  if (expandedTables.value.has(index)) {
    expandedTables.value.delete(index)
  } else {
    expandedTables.value.add(index)
  }
}

/** 切换图片区域展开/折叠 */
function toggleImages(index: number) {
  if (expandedImages.value.has(index)) {
    expandedImages.value.delete(index)
  } else {
    expandedImages.value.add(index)
  }
}

/** 来源类型标签 */
function refTypeLabel(mediaType: string): string {
  return { image: '图表', table: '表格', text: '文本' }[mediaType] || '文本'
}

function askQuestion() {
  const q = question.value.trim()
  if (!q) return

  asking.value = true
  messages.value.push({ role: 'user', content: q })
  question.value = ''

  if (streamEnabled.value) {
    askWithStream(q)
  } else {
    askWithoutStream(q)
  }
}

/** 流式问答 */
function askWithStream(q: string) {
  const assistantMsg: Message = {
    role: 'assistant',
    content: '',
    confidence: 0,
    sources: [],
    tables: [],
    images: [],
  }
  messages.value.push(assistantMsg)
  const msgIndex = messages.value.length - 1

  askQuestionStream(
    { question: q, sessionId: currentSessionId.value },
    (meta) => {
      messages.value[msgIndex]._meta = meta
      if (meta.sessionId) {
        currentSessionId.value = meta.sessionId
      }
    },
    (text) => {
      messages.value[msgIndex].content += text
      nextTick().then(() => {
        if (chatRef.value) {
          chatRef.value.scrollTop = chatRef.value.scrollHeight
        }
      })
    },
    (result) => {
      messages.value[msgIndex].content = result.answer
      messages.value[msgIndex].confidence = result.confidence
      messages.value[msgIndex].sources = result.sources || []
      const meta = messages.value[msgIndex]._meta
      if (meta) {
        messages.value[msgIndex].tables = meta.tables || []
        messages.value[msgIndex].images = meta.images || []
        if (meta.tables?.length > 0) expandedTables.value.add(msgIndex)
        if (meta.images?.length > 0) expandedImages.value.add(msgIndex)
        if (result.sources?.length > 0) expandedRefs.value.add(msgIndex)
      }
      asking.value = false
      loadSessions()
    },
    (error) => {
      messages.value[msgIndex].content = '抱歉，系统暂时无法处理您的请求: ' + error
      asking.value = false
    }
  )
}

/** 非流式问答 */
async function askWithoutStream(q: string) {
  try {
    const res = await askQuestion({ question: q, sessionId: currentSessionId.value })
    const data = res.data
    messages.value.push({
      role: 'assistant',
      content: data.answer || '',
      confidence: data.confidence || 0,
      sources: data.sources || [],
      tables: data.tables || [],
      images: data.images || [],
    })
    const msgIndex = messages.value.length - 1
    if (data.sources?.length > 0) expandedRefs.value.add(msgIndex)
    if (data.images?.length > 0) expandedImages.value.add(msgIndex)
    // 非流式：表格去重后展示
    if (data.tables?.length > 0) {
      const filtered = getFilteredTables(messages.value[msgIndex])
      if (filtered.length > 0) expandedTables.value.add(msgIndex)
    }
    asking.value = false
    loadSessions()
    nextTick().then(() => {
      if (chatRef.value) {
        chatRef.value.scrollTop = chatRef.value.scrollHeight
      }
    })
  } catch (e: any) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，系统暂时无法处理您的请求: ' + (e.message || '未知错误'),
      confidence: 0,
      sources: [],
    })
    asking.value = false
  }
}

/** 流式开关切换时持久化到后端 */
function onStreamToggle(val: boolean) {
  updateSettings({ qa_stream_enabled: val ? 'true' : 'false' }).catch(() => {})
}

function newSession() {
  currentSessionId.value = 'session_' + Date.now()
  messages.value = []
}

async function loadSessions() {
  try {
    const res = await getQAChatSessions()
    sessions.value = res.data || []
  } catch (e) { console.error(e) }
}

async function loadSession(sessionId: string) {
  currentSessionId.value = sessionId
  try {
    const res = await getQAChatSession(sessionId)
    messages.value = []
    for (const item of (res.data || [])) {
      messages.value.push({ role: 'user', content: item.question })
      messages.value.push({
        role: 'assistant',
        content: item.answer,
        confidence: item.confidence,
        sources: [],
      })
    }
  } catch (e) { console.error(e) }
}

async function deleteSession(sessionId: string) {
  try {
    await ElMessageBox.confirm('确定删除该会话及其所有记录？', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteQAChatSession(sessionId)
    ElMessage.success('会话已删除')
    loadSessions()
    if (currentSessionId.value === sessionId) {
      newSession()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

onMounted(async () => {
  loadSessions()
  // 从后端加载流式开关配置
  try {
    const res = await getSettings()
    const settings = res.data || {}
    if (settings.qa_stream_enabled !== undefined) {
      streamEnabled.value = settings.qa_stream_enabled === 'true'
    }
  } catch (e) {
    console.warn('加载流式配置失败，使用默认值', e)
  }
})
</script>

<style scoped>
.smart-qa { background: white; border-radius: 8px; padding: 16px; }

.session-panel {
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  height: 600px;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #E2E8F0;
  font-weight: bold;
  font-size: 13px;
  color: #334155;
}
.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 8px; border-radius: 6px; cursor: pointer; margin-bottom: 4px;
}
.session-item:hover { background: #F5F7FA; }
.session-item.active { background: #E8F0FE; }
.session-title { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 120px; }
.session-actions { display: flex; align-items: center; gap: 6px; }
.delete-btn { cursor: pointer; color: #94A3B8; font-size: 14px; transition: color 0.2s; }
.delete-btn:hover { color: #E53E3E; }

.chat-panel {
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  height: 600px;
  display: flex;
  flex-direction: column;
}
.chat-header {
  padding: 12px 16px;
  border-bottom: 1px solid #E2E8F0;
  display: flex; justify-content: space-between; align-items: center;
}
.chat-header-right { display: flex; align-items: center; gap: 12px; }
.chat-title { font-weight: bold; color: #334155; }
.chat-hint { font-size: 11px; color: #94A3B8; }

.chat-messages { flex: 1; overflow-y: auto; padding: 16px; }

/* 消息布局 */
.message { margin-bottom: 16px; display: flex; }
.message.user { justify-content: flex-end; }
.message.assistant { justify-content: flex-start; }

/* 用户气泡 */
.user-bubble {
  max-width: 70%;
  padding: 10px 16px;
  border-radius: 12px;
  border-bottom-right-radius: 4px;
  background: #4A90D9;
  color: white;
  font-size: 14px;
  line-height: 1.6;
}

/* 助手消息容器 - 不限宽度 */
.message-wrapper {
  max-width: 85%;
  min-width: 300px;
}

/* 助手回答文字 */
.answer-text {
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 12px;
  border-bottom-left-radius: 4px;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.7;
  color: #334155;
}

.confidence {
  font-size: 11px;
  color: #94A3B8;
  margin-top: 4px;
  text-align: right;
}

.chat-input-area {
  display: flex;
  gap: 8px;
  padding: 12px;
  border-top: 1px solid #E2E8F0;
}

/* 嵌入回答中的表格样式 */
.tables-inline {
  margin-top: 16px;
  border-top: 1px solid #E2E8F0;
  padding-top: 16px;
}
.embedded-table {
  margin: 12px 0;
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  padding: 12px;
}
.table-caption {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}
.embedded-table :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 0;
}
.embedded-table :deep(th),
.embedded-table :deep(td) {
  border: 1px solid #E2E8F0;
  padding: 8px 12px;
  text-align: left;
}
.embedded-table :deep(th) {
  background: #F1F5F9;
  font-weight: 600;
}

/* 表格区域样式 */
.tables-section { margin-top: 10px; }
.tables-header {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  background: #FAFBFC;
}
.tables-header:hover { background: #F1F5F9; }
.tables-icon { font-size: 14px; }
.tables-title { font-size: 12px; font-weight: 600; color: #475569; flex: 1; }
.tables-toggle { font-size: 11px; color: #94A3B8; }
.tables-content { margin-top: 8px; }
.tables-content.collapsed { display: none; }
.table-item {
  margin-bottom: 10px;
  padding: 10px;
  background: white;
  border-radius: 8px;
  border: 1px solid #E2E8F0;
}
.media-title { font-size: 12px; font-weight: 600; color: #334155; margin-bottom: 6px; }
.markdown-table :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.markdown-table :deep(th),
.markdown-table :deep(td) {
  border: 1px solid #E2E8F0;
  padding: 6px 10px;
  text-align: left;
}
.markdown-table :deep(th) {
  background: #F1F5F9;
  font-weight: 600;
}

/* 图片区域样式 */
.images-section { margin-top: 10px; }
.images-header {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  background: #FAFBFC;
}
.images-header:hover { background: #F1F5F9; }
.images-icon { font-size: 14px; }
.images-title { font-size: 12px; font-weight: 600; color: #475569; flex: 1; }
.images-toggle { font-size: 11px; color: #94A3B8; }
.images-content { margin-top: 8px; }
.images-content.collapsed { display: none; }
.images-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.image-wrapper {
  cursor: pointer;
  border-radius: 4px;
  overflow: hidden;
  transition: box-shadow 0.2s, transform 0.15s;
}
.image-wrapper:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
  transform: scale(1.02);
}
.result-image {
  max-width: 100%;
  max-height: 300px;
  object-fit: contain;
  display: block;
  border-radius: 4px;
}
.preview-full-image {
  max-width: 85vw;
  max-height: 85vh;
  object-fit: contain;
  display: block;
  margin: 0 auto;
}
.image-preview-dialog :deep(.el-dialog__body) {
  padding: 10px;
}

/* 引用来源面板 (参考 llm_wiki CitedReferencesPanel) */
.references-panel {
  margin-top: 10px;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  background: #FAFBFC;
  overflow: hidden;
}
.references-header {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.references-header:hover { background: #F1F5F9; }
.references-icon { font-size: 14px; }
.references-title { font-size: 12px; font-weight: 600; color: #475569; flex: 1; }
.references-toggle { font-size: 11px; color: #94A3B8; }

.references-list { padding: 0 8px 8px; }
.references-list.collapsed { }

.reference-item {
  display: flex; align-items: center; gap: 8px;
  padding: 5px 8px;
  border-radius: 4px;
  font-size: 12px;
  transition: background 0.1s;
}
.reference-item:hover { background: #EEF2FF; }

.ref-index {
  font-size: 10px; color: #94A3B8; font-weight: 600;
  min-width: 22px; text-align: right;
}
.ref-type-badge {
  font-size: 9px; font-weight: 600;
  padding: 1px 6px; border-radius: 3px;
  white-space: nowrap;
}
.ref-type-image { background: #DBEAFE; color: #2563EB; }
.ref-type-table { background: #FEF3C7; color: #D97706; }
.ref-type-text { background: #F1F5F9; color: #64748B; }

.ref-title {
  flex: 1;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  color: #334155;
}

.references-more {
  padding: 4px 8px;
  font-size: 11px; color: #4A90D9; cursor: pointer;
}
.references-more:hover { text-decoration: underline; }

/* Markdown 渲染样式 */
.markdown-body { word-wrap: break-word; overflow-wrap: break-word; }
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) { margin: 10px 0 6px; font-weight: 600; line-height: 1.4; }
.markdown-body :deep(h1) { font-size: 1.2em; }
.markdown-body :deep(h2) { font-size: 1.1em; }
.markdown-body :deep(h3) { font-size: 1.02em; }
.markdown-body :deep(p) { margin: 5px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { margin: 5px 0; padding-left: 20px; }
.markdown-body :deep(li) { margin: 2px 0; }
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.06); padding: 1px 4px; border-radius: 3px;
  font-size: 0.9em; font-family: 'SFMono-Regular', Consolas, monospace;
}
.markdown-body :deep(pre) {
  background: #1E293B; color: #E2E8F0; padding: 12px;
  border-radius: 6px; overflow-x: auto; margin: 8px 0;
}
.markdown-body :deep(pre code) { background: transparent; padding: 0; color: inherit; }
.markdown-body :deep(blockquote) {
  border-left: 3px solid #4A90D9; padding-left: 12px;
  margin: 8px 0; color: #64748B;
}
.markdown-body :deep(table) {
  border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 12px;
}
.markdown-body :deep(table th), .markdown-body :deep(table td) {
  border: 1px solid #E2E8F0; padding: 6px 10px; text-align: left;
}
.markdown-body :deep(table th) { background: #F1F5F9; font-weight: 600; }
.markdown-body :deep(table tr:nth-child(even)) { background: #F8FAFC; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(em) { font-style: italic; }
.markdown-body :deep(a) { color: #4A90D9; text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid #E2E8F0; margin: 10px 0; }
.markdown-body :deep(img) { max-width: 100%; border-radius: 4px; }
</style>
