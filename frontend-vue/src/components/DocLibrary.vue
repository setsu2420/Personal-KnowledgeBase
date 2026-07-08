<template>
  <div class="libraries-section">
    <div class="libraries-header">
      <div class="libraries-title-wrap">
        <h2>知识库资料</h2>
        <span class="libraries-subtitle">上传并管理原文档</span>
      </div>
      <el-button type="primary" size="default" @click="$emit('uploadClick')">+ 上传新资料</el-button>
    </div>

    <div class="lib-list">
      <template v-if="loading">
        <div v-for="n in 4" :key="'skel-doc-' + n" class="doc-card" style="cursor: default; width: 220px; height: 150px;">
          <div class="doc-cover-wrapper" style="border-bottom: none; height: 100%;">
            <SkeletonLoader width="100%" height="100%" />
          </div>
        </div>
      </template>
      <template v-else>
        <div v-for="lib in libraries" :key="lib.key" class="lib-row">
          <div class="lib-row-header">
            <span class="lib-row-name">{{ lib.name }}</span>
          </div>
          <div class="lib-row-body">
            <template v-if="lib.key === 'chart'">
              <div v-if="docList[lib.key] && docList[lib.key].length > 0" class="doc-preview horizontal-scroll">
                <div
                  v-for="(doc, docIndex) in (docList[lib.key] || [])"
                  :key="doc.id"
                  class="doc-card chart-card"
                  :style="{ animationDelay: (docIndex % 20) * 0.04 + 's' }"
                  @click="$emit('docClick', doc)"
                >
                  <div class="doc-cover-wrapper chart-cover" style="border-bottom: none;">
                    <img :src="getDocFileUrl(doc.id)" class="doc-cover-img" alt="Chart Preview" />
                  </div>
                  <div v-if="doc.tableMarkdown" class="table-preview-mini" v-html="renderTablePreview(doc.tableMarkdown)"></div>
                </div>
              </div>
              <div v-else class="doc-empty">暂无上传图表</div>
            </template>
            <template v-else>
              <div v-if="docList[lib.key] && docList[lib.key].length > 0" class="doc-list-view">
                <div
                  v-for="(doc, docIndex) in (docList[lib.key] || [])"
                  :key="doc.id"
                  class="doc-list-item"
                  :style="{ animationDelay: (docIndex % 20) * 0.04 + 's' }"
                  @click="$emit('docClick', doc)"
                >
                  <el-icon class="doc-file-icon"><Document /></el-icon>
                  <span class="doc-title-link" :title="doc.title">{{ doc.title }}</span>
                  <span
                    class="doc-type-tag"
                    :style="getFileTypeStyle(getFileType(doc))"
                  >
                    {{ getFileType(doc) }}
                  </span>
                  <span class="doc-time-label">{{ doc.uploadTime }}</span>
                </div>
              </div>
              <div v-else class="doc-empty">暂无上传文档</div>
            </template>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import { getDocFileUrl } from '../api'
import { marked } from 'marked'
import SkeletonLoader from './SkeletonLoader.vue'

defineProps<{
  docList: Record<string, any[]>
  loading: boolean
}>()

defineEmits<{
  docClick: [doc: any]
  uploadClick: []
}>()

marked.setOptions({
  breaks: true,
  gfm: true,
})

const libraries = [
  { key: 'report', name: '研究报告库' },
  { key: 'dynamic', name: '动态信息库' },
  { key: 'translation', name: '译丛译著库' },
  { key: 'chart', name: '图表库' },
]

function getFileType(doc: any): string {
  if (!doc) return 'TXT'
  if (doc.url || (doc.title && (doc.title.startsWith('http://') || doc.title.startsWith('https://')))) {
    return 'LINK'
  }
  if (!doc.title) return 'TXT'
  const parts = doc.title.split('.')
  if (parts.length > 1) {
    const ext = parts[parts.length - 1].toUpperCase()
    if (ext === 'HTML' && doc.url) return 'LINK'
    return ext
  }
  if (doc.filePath) {
    const fParts = doc.filePath.split('.')
    return fParts[fParts.length - 1].toUpperCase()
  }
  return 'TXT'
}

function getFileTypeStyle(type: string) {
  const t = (type || '').toUpperCase()
  switch (t) {
    case 'PDF':
      return { color: '#ef4444', backgroundColor: '#fef2f2', border: '1px solid #fee2e2' }
    case 'DOC':
    case 'DOCX':
      return { color: '#3b82f6', backgroundColor: '#eff6ff', border: '1px solid #dbeafe' }
    case 'XLS':
    case 'XLSX':
    case 'CSV':
      return { color: '#10b981', backgroundColor: '#ecfdf5', border: '1px solid #d1fae5' }
    case 'PPT':
    case 'PPTX':
      return { color: '#f59e0b', backgroundColor: '#fffbeb', border: '1px solid #fef3c7' }
    case 'TXT':
    case 'MD':
    case 'MARKDOWN':
      return { color: '#6b7280', backgroundColor: '#f9fafb', border: '1px solid #f3f4f6' }
    case 'LINK':
      return { color: '#8b5cf6', backgroundColor: '#f5f3ff', border: '1px solid #ddd6fe' }
    default:
      return { color: '#6366f1', backgroundColor: '#eef2ff', border: '1px solid #e0e7ff' }
  }
}

function renderTablePreview(markdown: string): string {
  if (!markdown) return ''
  return marked.parse(markdown) as string
}
</script>

<style scoped>
.libraries-section {
  padding: 24px;
  background: white;
  border-radius: 10px;
  border: 1px solid #E2E8F0;
  margin-bottom: 24px;
}
.libraries-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.libraries-title-wrap h2 {
  font-size: 20px;
  color: #1E293B;
  font-weight: 700;
  margin: 0 0 4px;
}
.libraries-subtitle {
  font-size: 12px;
  color: #64748B;
}
.lib-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.lib-row {
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.02);
}
.lib-row-header {
  background: linear-gradient(180deg, #F8FAFC, #EFF6FF);
  padding: 12px 18px;
  border-bottom: 1px solid #E2E8F0;
}
.lib-row-name {
  font-size: 14px;
  font-weight: bold;
  color: #1E3A8A;
}
.lib-row-body {
  padding: 16px 20px;
  background: white;
}
.doc-preview {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  width: 100%;
}
.doc-card {
  background: white;
  border-radius: 8px;
  border: 1px solid #E2E8F0;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  height: 240px;
  transition: all 0.2s ease-in-out;
  box-sizing: border-box;
  animation: docSlideUpFade 0.35s ease-out both;
}
.doc-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.doc-cover-wrapper {
  flex: 1;
  background: #F8FAFC;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  border-bottom: 1px solid #E2E8F0;
  height: 170px;
}
.doc-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: top;
}
.doc-preview.horizontal-scroll {
  display: flex;
  overflow-x: auto;
  gap: 16px;
  padding-bottom: 12px;
  width: 100%;
}
.doc-preview.horizontal-scroll::-webkit-scrollbar {
  height: 6px;
}
.doc-preview.horizontal-scroll::-webkit-scrollbar-thumb {
  background: #CBD5E1;
  border-radius: 3px;
}
.doc-preview.horizontal-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.chart-card {
  flex-shrink: 0;
  width: 220px;
  height: 150px;
  cursor: pointer;
}
.chart-cover {
  height: 100%;
}
.doc-list-view {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.doc-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: white;
  border-radius: 6px;
  border: 1px solid #E2E8F0;
  transition: all 0.2s ease;
  cursor: pointer;
  animation: docSlideUpFade 0.35s ease-out both;
}
.doc-list-item:hover {
  background: #EFF6FF;
  border-color: #BFDBFE;
  transform: translateX(2px);
}
.doc-file-icon {
  font-size: 16px;
  color: #64748B;
}
.doc-title-link {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.doc-type-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}
.doc-time-label {
  font-size: 11px;
  color: #94A3B8;
}
.doc-empty {
  font-size: 13px;
  color: #94A3B8;
  text-align: center;
  padding: 8px 0;
}
.table-preview-mini {
  font-size: 11px;
  max-height: 150px;
  overflow: auto;
  border-top: 1px solid #E2E8F0;
  padding: 6px;
}
.table-preview-mini :deep(table) {
  width: 100%;
  border-collapse: collapse;
  font-size: 10px;
}
.table-preview-mini :deep(th),
.table-preview-mini :deep(td) {
  border: 1px solid #E2E8F0;
  padding: 2px 4px;
  text-align: left;
}
.table-preview-mini :deep(th) {
  background: #F1F5F9;
  font-weight: bold;
}

@keyframes docSlideUpFade {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
