<template>
  <div class="smart-qa">
    <div class="qa-layout">
      <!-- 左侧：对话历史 -->
      <div :class="['session-sidebar', { collapsed: isHistoryCollapsed }]">
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
      </div>

      <!-- 右侧：对话区 -->
      <div class="chat-main">
        <div class="chat-panel">
          <div class="chat-header">
            <div class="chat-header-left">
              <el-button 
                class="toggle-sidebar-btn" 
                type="text" 
                @click="isHistoryCollapsed = !isHistoryCollapsed"
                :title="isHistoryCollapsed ? '展开历史会话' : '收起历史会话'"
              >
                <el-icon size="18">
                  <Expand v-if="isHistoryCollapsed" />
                  <Fold v-else />
                </el-icon>
              </el-button>
              <span class="chat-title">智能问答</span>
            </div>
            <div class="chat-header-right">
              <el-switch v-model="streamEnabled" size="small" active-text="流式" inactive-text="普通" @change="onStreamToggle" />
              <span class="chat-hint">语义检索 · 多文档交叉验证 · 来源溯源</span>
            </div>
          </div>

          <div class="chat-messages" ref="chatRef">
            <TransitionGroup name="msg-slide" tag="div">
              <div v-for="(msg, i) in messages" :key="i" :class="['message', msg.role]">
                <div :class="msg.role === 'user' ? 'message-bubble user-bubble' : 'message-wrapper'">
                  <!-- 用户消息 -->
                  <div v-if="msg.role === 'user'" class="bubble-text" v-html="formatText(msg.content, 'user')" />

                  <!-- 助手消息 -->
                  <template v-else>
                    <!-- 文字回答（表格由 LLM 通过 markdown 自然生成） -->
                    <div class="answer-wrapper">
                      <div class="answer-text markdown-body" v-html="formatText(msg.content, 'assistant')" />
                      <button class="copy-btn" @click="copyAnswer(msg.content)" title="复制回答">
                        <el-icon><DocumentCopy /></el-icon>
                      </button>
                    </div>

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

                    <!-- 图片区域（去重后显示，排除已在表格中出现的图片） -->
                    <div v-if="getFilteredImages(msg).length > 0" class="images-section">
                      <div class="images-header" @click="toggleImages(i)">
                        <span class="images-icon">&#128444;</span>
                        <span class="images-title">相关图片 ({{ getFilteredImages(msg).length }})</span>
                        <span class="images-toggle">{{ expandedImages.has(i) ? '收起' : '展开' }}</span>
                      </div>
                      <div class="images-content" :class="{ collapsed: !expandedImages.has(i) }">
                        <div class="images-grid">
                          <div v-for="(img, ii) in getFilteredImages(msg)" :key="'i'+ii" class="image-wrapper" @click="previewImage(img)">
                            <span class="image-index">图{{ String(ii + 1).padStart(2, '0') }}</span>
                            <img
                              :src="getImageUrl(img.media_path || '')"
                              class="result-image"
                              :alt="img.title || '智能问答来源图片'"
                              @error="handleImageError"
                            />
                            <div class="image-error" style="display:none; padding:20px; background:#f5f5f5; text-align:center; color:#999; border-radius:4px;">
                              图片加载失败
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
            </TransitionGroup>
            <el-empty v-if="messages.length === 0" description="输入问题开始对话" />
          </div>

          <div class="chat-input-area">
            <div class="mention-input-wrapper">
              <!-- @mention badges above input -->
              <div v-if="mentionSelected.length > 0" class="mention-badges">
                <el-tag
                  v-for="doc in mentionSelected"
                  :key="doc.id"
                  closable
                  type="primary"
                  size="small"
                  @close="removeMention(doc.id)"
                  class="mention-tag"
                >
                  📎 {{ doc.title }}
                </el-tag>
              </div>
              <textarea
                ref="inputRef"
                v-model="question"
                class="chat-textarea"
                :placeholder="'输入您的问题，输入 @ 提及文件...' + (asking ? '（正在思考...）' : '')"
                :disabled="asking"
                @keyup.enter.exact.prevent="askQuestion"
                @input="handleInputChange"
                @keydown.escape="mentionVisible = false"
                rows="2"
              />
              <!-- @mention dropdown -->
              <div v-if="mentionVisible" class="mention-dropdown">
                <div
                  v-for="doc in mentionFilter"
                  :key="doc.id"
                  class="mention-item"
                  @click="selectMention(doc)"
                >
                  <el-icon class="mention-icon"><Document /></el-icon>
                  <span class="mention-title">{{ doc.title }}</span>
                  <el-tag size="small" type="info" class="mention-type">{{ doc.docType }}</el-tag>
                </div>
                <div v-if="mentionFilter.length === 0" class="mention-empty">没有匹配的文件</div>
              </div>
            </div>
            <div class="input-actions">
              <el-button type="primary" size="large" @click="askQuestion" :loading="asking" :disabled="asking || !question.trim()">
                提问
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <!-- 图片预览弹窗 -->
    <el-dialog v-model="previewVisible" width="fit-content" top="5vh" :show-close="true" class="image-preview-dialog" append-to-body>
      <img :src="previewUrl" class="preview-full-image" alt="图片预览" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { Delete, DocumentCopy, Document, Fold, Expand } from '@element-plus/icons-vue'
import { askQuestion as askQuestionApi, askQuestionStream, getQAChatSessions, getQAChatSession, deleteQAChatSession, getMediaUrl, getSettings, updateSettings, getDocuments } from '../../api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { renderPreviewMarkdown } from '../../utils/previewFormatting'

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
const expandedImages = ref<Set<number>>(new Set())
const previewVisible = ref(false)
const previewUrl = ref('')
const streamEnabled = ref(true) // 流式回答开关，默认开启
const isHistoryCollapsed = ref(false) // 历史会话收起状态

// @mention state
const mentionVisible = ref<boolean>(false)
const mentionQuery = ref<string>('')
const mentionDocs = ref<any[]>([])
const mentionSelected = ref<Array<{id: number, title: string}>>([])
const inputRef = ref<HTMLTextAreaElement | null>(null)

const mentionFilter = computed(() => {
  if (!mentionQuery.value) return mentionDocs.value.slice(0, 20)
  const q = mentionQuery.value.toLowerCase()
  return mentionDocs.value.filter(d => d.title?.toLowerCase().includes(q)).slice(0, 20)
})

function formatText(text: string, role: 'user' | 'assistant' = 'assistant'): string {
  if (!text) return ''
  if (role === 'user') {
    return text.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\n/g, '<br>')
  }
  return renderPreviewMarkdown(text)
}

/** 过滤图片：排除已在表格中出现的图片（按ID去重），确保每个图表只出现一次 */
function getFilteredImages(msg: Message): Array<any> {
  if (!msg.images || msg.images.length === 0) return []
  const tableIds = new Set((msg.tables || []).map((t: any) => t.id))
  return msg.images.filter((img: any) => !tableIds.has(img.id))
}

/** 一键复制回答内容 */
async function copyAnswer(content: string) {
  if (!content) {
    ElMessage.warning('暂无可复制的内容')
    return
  }
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    // fallback for older browsers
    const textarea = document.createElement('textarea')
    textarea.value = content
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    document.body.appendChild(textarea)
    textarea.select()
    try {
      document.execCommand('copy')
      ElMessage.success('已复制到剪贴板')
    } catch (err) {
      ElMessage.error('复制失败，请手动选择复制')
    }
    document.body.removeChild(textarea)
  }
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
  let q = question.value.trim()
  if (!q) return

  // Append referenced file names to the question
  if (mentionSelected.value.length > 0) {
    const titles = mentionSelected.value.map(m => m.title).join(', ')
    q += '\n\n[引用文件: ' + titles + ']'
    mentionSelected.value = []
  }

  asking.value = true
  messages.value.push({ role: 'user', content: q })
  question.value = ''

  if (streamEnabled.value) {
    askWithStream(q)
  } else {
    askWithoutStream(q)
  }
}

function handleInputChange(e: Event) {
  const target = e.target as HTMLTextAreaElement
  const val = target.value
  const cursorPos = target.selectionStart || 0
  // Detect @ trigger
  const textBeforeCursor = val.substring(0, cursorPos)
  const atMatch = textBeforeCursor.match(/@([^\s@]*)$/)
  if (atMatch) {
    mentionQuery.value = atMatch[1]
    mentionVisible.value = true
    if (mentionDocs.value.length === 0) {
      loadMentionDocs()
    }
  } else {
    mentionVisible.value = false
    mentionQuery.value = ''
  }
}

function selectMention(doc: any) {
  // Remove the @query text from input
  const textarea = inputRef.value
  if (textarea) {
    const cursorPos = textarea.selectionStart || 0
    const val = question.value
    const textBefore = val.substring(0, cursorPos)
    const atIndex = textBefore.lastIndexOf('@')
    if (atIndex >= 0) {
      question.value = val.substring(0, atIndex) + val.substring(cursorPos)
    }
  }
  // Add to selected mentions if not already present
  if (!mentionSelected.value.some(m => m.id === doc.id)) {
    mentionSelected.value.push({ id: doc.id, title: doc.title })
  }
  mentionVisible.value = false
  mentionQuery.value = ''
  nextTick(() => inputRef.value?.focus())
}

function removeMention(docId: number) {
  mentionSelected.value = mentionSelected.value.filter(m => m.id !== docId)
}

async function loadMentionDocs() {
  try {
    const res = await getDocuments({ page: 1, pageSize: 100 })
    mentionDocs.value = res.data.items || []
  } catch (e) {
    console.error('Failed to load mention docs', e)
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
        if (meta.images?.length > 0) expandedImages.value.add(msgIndex)
        if (result.sources && result.sources.length > 0) expandedRefs.value.add(msgIndex)
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
    const res = await askQuestionApi({ question: q, sessionId: currentSessionId.value })
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
        sources: parseSourcesString(item.sources),
      })
    }
  } catch (e) { console.error(e) }
}

/** 解析数据库中的来源字符串为数组 */
function parseSourcesString(sourcesStr: string | undefined): Array<any> {
  if (!sourcesStr) return []
  try {
    const parsed = JSON.parse(sourcesStr)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
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
.qa-layout { display: flex; gap: 16px; width: 100%; align-items: flex-start; }
.session-sidebar { width: 25%; transition: all 0.3s ease; overflow: hidden; flex-shrink: 0; }
.session-sidebar.collapsed { width: 0; margin-right: -16px; opacity: 0; pointer-events: none; }
.chat-main { flex: 1; min-width: 0; transition: all 0.3s ease; }
.chat-header-left { display: flex; align-items: center; gap: 8px; }
.toggle-sidebar-btn { padding: 0; min-height: unset; color: #64748B; cursor: pointer; margin-right: 4px; }
.toggle-sidebar-btn:hover { color: #3b82f6; }

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

/* 助手回答包装器 */
.answer-wrapper {
  position: relative;
}

/* 助手回答文字 */
.answer-text {
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 12px;
  border-bottom-left-radius: 4px;
  padding: 14px 18px;
  padding-right: 48px;
  font-size: 14px;
  line-height: 1.7;
  color: #334155;
}

/* 复制按钮 */
.copy-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #94A3B8;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.copy-btn:hover {
  background: #E2E8F0;
  color: #475569;
}

/* 图片序号标签 */
.image-index {
  position: absolute;
  top: 6px;
  left: 6px;
  background: rgba(0,0,0,0.6);
  color: white;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  z-index: 1;
}
.image-wrapper {
  position: relative;
}

.confidence {
  font-size: 11px;
  color: #94A3B8;
  margin-top: 4px;
  text-align: right;
}

.chat-input-area {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #E2E8F0;
  background: #FAFBFE;
  border-radius: 0 0 8px 8px;
}

.mention-input-wrapper {
  flex: 1;
  position: relative;
}

.chat-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 14px;
  border: 1px solid #DBEAFE;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
  min-height: 44px;
  max-height: 120px;
  overflow-y: auto;
  line-height: 1.5;
  background: #F8FAFC;
  color: #1E293B;
}

.chat-textarea:focus {
  border-color: #3B82F6;
  background: white;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

.chat-textarea:disabled {
  background: #F1F5F9;
  color: #94A3B8;
  cursor: not-allowed;
}

.mention-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}

.mention-tag {
  max-width: 200px;
}

.mention-dropdown {
  position: absolute;
  bottom: calc(100% + 4px);
  left: 0;
  right: 0;
  background: white;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  max-height: 240px;
  overflow-y: auto;
  z-index: 1000;
}

.mention-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: background 0.15s;
  border-bottom: 1px solid #F1F5F9;
}

.mention-item:last-child {
  border-bottom: none;
}

.mention-item:hover {
  background: #EFF6FF;
}

.mention-icon {
  color: #64748B;
  font-size: 14px;
  flex-shrink: 0;
}

.mention-title {
  flex: 1;
  font-size: 13px;
  color: #1E293B;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mention-type {
  flex-shrink: 0;
  font-size: 11px;
}

.mention-empty {
  padding: 16px;
  text-align: center;
  color: #94A3B8;
  font-size: 13px;
}

.input-actions {
  display: flex;
  align-items: flex-end;
  padding-bottom: 2px;
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

/* 消息气泡过渡动画 */
.msg-slide-enter-active {
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}
.msg-slide-leave-active {
  transition: all 0.2s ease-in;
}
.msg-slide-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.msg-slide-enter-to {
  opacity: 1;
  transform: translateY(0);
}
.msg-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}
</style>
