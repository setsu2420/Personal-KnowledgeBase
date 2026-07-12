import { marked } from 'marked'

marked.setOptions({
  breaks: true,
  gfm: true,
})

const MENTION_PATTERN = /(^|[\s(])(@[^\s`<>()\[\]{}]+)/g

export function extractMentionTokens(text: string): string[] {
  if (!text) return []

  const seen = new Set<string>()
  const tokens: string[] = []

  for (const match of text.matchAll(MENTION_PATTERN)) {
    const token = (match[2] || '').trim().replace(/[.,;:!?。！？、，；：]+$/, '')
    if (!token || seen.has(token)) continue
    seen.add(token)
    tokens.push(token)
  }

  return tokens
}

export function renderPreviewMarkdown(text: string): string {
  if (!text) return ''

  const highlighted = text.replace(MENTION_PATTERN, (...args: string[]) => {
    const [, prefix, token] = args
    const normalizedToken = token.replace(/[.,;:!?。！？、，；：]+$/, '')
    return `${prefix}<span class="preview-mention">${normalizedToken}</span>`
  })

  return marked.parse(highlighted) as string
}
