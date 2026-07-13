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

export function preprocessMarkdown(text: string): string {
  if (!text) return ''

  const lines = text.split('\n')
  const newLines: string[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()

    // Check if the current line looks like a table separator line
    const isSeparator = 
      trimmed.startsWith('|') && 
      /^[|:\-\s]+$/.test(trimmed) && 
      trimmed.includes('-')

    if (isSeparator) {
      // 1. Normalize the separator line
      const parts = line.split('|')
      const normalizedParts = parts.map((p, idx) => {
        if (idx === 0 && p.trim() === '') return ''
        if (idx === parts.length - 1 && p.trim() === '') return ''
        const cell = p.trim()
        if (!cell) return ' '
        const left = cell.startsWith(':') ? ':' : ''
        const right = cell.endsWith(':') ? ':' : ''
        const dashes = cell.replace(/:/g, '').replace(/\s+/g, '-').replace(/-+/g, '---')
        return ' ' + left + dashes + right + ' '
      })
      const normalizedSeparator = normalizedParts.join('|')

      // 2. Ensure there is a header line before it, and that header line is preceded by an empty line
      if (newLines.length > 0) {
        const prevLine = newLines[newLines.length - 1]
        if (newLines.length > 1) {
          const prePrevLine = newLines[newLines.length - 2]
          if (prePrevLine.trim() !== '' && !prePrevLine.trim().startsWith('|') && prevLine.trim().startsWith('|')) {
            newLines.splice(newLines.length - 1, 0, '')
          }
        } else if (newLines.length === 1 && prevLine.trim().startsWith('|')) {
          newLines.unshift('')
        }
      }

      newLines.push(normalizedSeparator)
    } else {
      const isHeaderCandidate = trimmed.startsWith('|')
      if (isHeaderCandidate && newLines.length > 0) {
        const prevLine = newLines[newLines.length - 1]
        if (prevLine.trim() !== '' && !prevLine.trim().startsWith('|')) {
          const nextLine = lines[i + 1]
          if (nextLine) {
            const nextTrimmed = nextLine.trim()
            const nextIsSeparator = 
              nextTrimmed.startsWith('|') && 
              /^[|:\-\s]+$/.test(nextTrimmed) && 
              nextTrimmed.includes('-')
            if (nextIsSeparator) {
              newLines.push('')
            }
          }
        }
      }
      newLines.push(line)
    }
  }

  return newLines.join('\n')
}

export function renderPreviewMarkdown(text: string): string {
  if (!text) return ''

  const preprocessed = preprocessMarkdown(text)

  const highlighted = preprocessed.replace(MENTION_PATTERN, (...args: string[]) => {
    const [, prefix, token] = args
    const normalizedToken = token.replace(/[.,;:!?。！？、，；：]+$/, '')
    return `${prefix}<span class="preview-mention">${normalizedToken}</span>`
  })

  return marked.parse(highlighted) as string
}
