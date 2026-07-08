<template>
  <div class="wiki-entries-section">
    <div class="wiki-section-header">
      <h2>词条百科</h2>
      <span class="wiki-subtitle">点击词条可查看详情 · 共 {{ filteredEntries.length }} 条</span>
    </div>

    <!-- 搜索工具栏 -->
    <div class="filter-toolbar">
      <el-row style="width: 100%; margin: 0; align-items: center;">
        <el-col :span="24">
          <el-input
            v-model="searchQuery"
            placeholder="搜索词条名称、内容或关键词..."
            clearable
            :prefix-icon="Search"
            size="small"
          />
        </el-col>
      </el-row>
    </div>

    <!-- 百科词条紧凑列表 -->
    <div class="wiki-list">
      <template v-if="loading">
        <div v-for="n in 5" :key="'skeleton-' + n" class="wiki-list-item">
          <SkeletonLoader :width="n === 1 ? '50%' : n === 2 ? '65%' : n === 3 ? '45%' : n === 4 ? '70%' : '55%'" height="14px" />
        </div>
      </template>
      <template v-else>
        <div
          v-for="(item, index) in paginatedEntries"
          :key="item.id"
          class="wiki-list-item"
          :class="{ 'wiki-list-item-selected': selectedEntryId === item.id, 'wiki-list-item-even': index % 2 === 1 }"
          :style="{ animationDelay: (index % 20) * 0.04 + 's' }"
          @click="$emit('entryClick', item)"
        >
          <!-- 1. 图片类型：显示小图标 + 名称 + 类型标签 -->
          <template v-if="item.entryType === 'image'">
            <el-icon class="wiki-type-icon" :style="{ color: getTypeColor(item.entryType) }"><Picture /></el-icon>
            <span class="wiki-list-title" :title="item.title">{{ item.title }}</span>
            <span class="wiki-type-badge" :style="{ color: getTypeColor(item.entryType), backgroundColor: getTypeColor(item.entryType) + '18' }">图片</span>
          </template>

          <!-- 2. 表格类型：显示小图标 + 名称 + 类型标签 -->
          <template v-else-if="item.entryType === 'table'">
            <el-icon class="wiki-type-icon" :style="{ color: getTypeColor(item.entryType) }"><Grid /></el-icon>
            <span class="wiki-list-title" :title="item.title">{{ item.title }}</span>
            <span class="wiki-type-badge" :style="{ color: getTypeColor(item.entryType), backgroundColor: getTypeColor(item.entryType) + '18' }">表格</span>
          </template>

          <!-- 3. 其他类型：显示类型图标 + 名称 + 类型标签 -->
          <template v-else>
            <el-icon class="wiki-type-icon" :style="{ color: getTypeColor(item.entryType) }">
              <component :is="getTypeIcon(item.entryType)" />
            </el-icon>
            <span class="wiki-list-title" :title="item.title">{{ item.title }}</span>
            <span class="wiki-type-badge" :style="{ color: getTypeColor(item.entryType), backgroundColor: getTypeColor(item.entryType) + '18' }">{{ getTypeBadgeLabel(item.entryType) }}</span>
          </template>
        </div>
      </template>
    </div>

    <!-- 分页组件 -->
    <div class="wiki-pagination" v-if="filteredEntries.length > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        layout="prev, pager, next, total"
        :total="filteredEntries.length"
        background
        small
      />
    </div>

    <el-empty v-if="!loading && filteredEntries.length === 0" description="没有找到匹配的词条数据" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { Search, Picture, Grid } from '@element-plus/icons-vue'
import SkeletonLoader from './SkeletonLoader.vue'

interface KnowledgeEntry {
  id: number
  title: string
  entryType: string
  library: string
  documentId?: number
  sourceName?: string
  content: string
  keywords: string
  status: string
  confidence?: number
  createdAt: string
  mediaType?: string
  mediaPath?: string
  sourceOrigin?: string
  tableMarkdown?: string
  projectId?: number
  description?: string
  related?: string
}

const props = defineProps<{
  entries: KnowledgeEntry[]
  loading: boolean
  selectedEntryId?: number | null
}>()

const emit = defineEmits<{
  entryClick: [item: KnowledgeEntry]
  search: [query: string]
}>()

const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const typeColors: Record<string, string> = {
  concept: '#c084fc',
  entity: '#60a5fa',
  thesis: '#8b5cf6',
  methodology: '#4ade80',
  finding: '#f87171',
  comparison: '#ec4899',
  synthesis: '#6366f1',
  table: '#fbbf24',
  image: '#f43f5e',
}

function getTypeColor(type: string): string {
  return typeColors[type] || '#cbd5e1'
}

function getTypeIcon(type: string): string {
  return 'Document'
}

function getTypeBadgeLabel(type: string): string {
  const labelMap: Record<string, string> = {
    concept: '概念',
    entity: '实体',
    thesis: '论点',
    methodology: '方法论',
    finding: '发现',
    comparison: '对比',
    synthesis: '综合',
    table: '表格',
    image: '图片',
  }
  return labelMap[type] || '词条'
}

const filteredEntries = computed(() => {
  return props.entries.filter(item => {
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase()
      const title = (item.title || '').toLowerCase()
      const content = (item.content || '').toLowerCase()
      const keywords = (item.keywords || '').toLowerCase()

      const titleMatch = title.includes(q)
      const contentMatch = content.includes(q)
      const kwMatch = keywords.includes(q)

      if (!titleMatch && !contentMatch && !kwMatch) return false
    }
    return true
  })
})

const paginatedEntries = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredEntries.value.slice(start, end)
})

let searchTimeout: any = null
watch(searchQuery, () => {
  currentPage.value = 1
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    emit('search', searchQuery.value)
  }, 300)
})

onUnmounted(() => {
  if (searchTimeout) clearTimeout(searchTimeout)
})
</script>

<style scoped>
.wiki-entries-section {
  padding: 24px;
  background: #F8FAFC;
  border-radius: 10px;
  border: 1px solid #E2E8F0;
  margin-bottom: 24px;
}
.wiki-section-header {
  margin-bottom: 20px;
}
.wiki-section-header h2 {
  font-size: 20px;
  color: #1E293B;
  font-weight: 700;
  margin: 0 0 4px;
}
.wiki-subtitle {
  font-size: 12px;
  color: #64748B;
}
.filter-toolbar {
  background: white;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #E2E8F0;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
}
.wiki-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 12px;
}
.wiki-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  background: white;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.18s ease;
  border: 1px solid transparent;
  animation: wikiSlideUpFade 0.35s ease-out both;
}
.wiki-list-item-even {
  background: #F8FAFC;
}
.wiki-list-item:hover {
  background: #EFF6FF;
  border-color: #BFDBFE;
  transform: translateX(2px);
  box-shadow: 0 1px 4px rgba(47, 84, 150, 0.08);
}
.wiki-list-item-selected {
  background: #EFF6FF !important;
  border-color: #BFDBFE !important;
}
.wiki-type-icon {
  flex-shrink: 0;
  font-size: 15px;
}
.wiki-list-title {
  flex: 1;
  font-size: 13px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.wiki-type-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 4px;
  letter-spacing: 0.5px;
}
.wiki-pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
}

@keyframes wikiSlideUpFade {
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
