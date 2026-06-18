import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const srcDir = path.join(rootDir, 'src')
const sourceExtensions = new Set(['.css', '.vue'])
const vueFiles = []
const sourceFiles = []

async function collectFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      await collectFiles(fullPath)
      continue
    }
    if (!entry.isFile()) continue

    const extension = path.extname(entry.name)
    if (sourceExtensions.has(extension)) {
      sourceFiles.push(fullPath)
    }
    if (extension === '.vue') {
      vueFiles.push(fullPath)
    }
  }
}

function relativePath(filePath) {
  return path.relative(rootDir, filePath)
}

function lineNumber(source, index) {
  return source.slice(0, index).split('\n').length
}

function compact(value) {
  return value.replace(/\s+/g, ' ').trim()
}

function addRegexViolations(violations, source, filePath, rule, regex) {
  for (const match of source.matchAll(regex)) {
    violations.push({
      filePath,
      line: lineNumber(source, match.index ?? 0),
      rule,
      snippet: compact(match[0])
    })
  }
}

function findDeclarationValueViolations(violations, source, filePath, property, rule, predicate) {
  const declarationRegex = new RegExp(`${property}\\s*:\\s*([\\s\\S]*?);`, 'gi')
  for (const match of source.matchAll(declarationRegex)) {
    const value = compact(match[1] ?? '')
    if (!predicate(value)) continue

    violations.push({
      filePath,
      line: lineNumber(source, match.index ?? 0),
      rule,
      snippet: compact(match[0])
    })
  }
}

async function collectCssVariableDefinitions() {
  const definitions = new Set()
  const definitionRegex = /(?:^|[{\s;])(--[a-z0-9-]+)\s*:/gim
  const dynamicDefinitionRegex = /['"](--[a-z0-9-]+)['"]\s*:/gim

  for (const filePath of sourceFiles) {
    const source = await readFile(filePath, 'utf8')
    for (const match of source.matchAll(definitionRegex)) {
      definitions.add(match[1])
    }
    for (const match of source.matchAll(dynamicDefinitionRegex)) {
      definitions.add(match[1])
    }
  }

  return definitions
}

function findBoxShadowViolations(violations, source, filePath) {
  findDeclarationValueViolations(
    violations,
    source,
    filePath,
    'box-shadow',
    'box-shadow 必须使用 shadow/ring/inset token，或显式 none',
    (value) => value !== 'none' && !value.startsWith('var(')
  )
}

function findUndefinedTokenViolations(violations, source, filePath, definitions) {
  const usageRegex = /var\(\s*(--[a-z0-9-]+)\b/gim
  for (const match of source.matchAll(usageRegex)) {
    const token = match[1]
    if (definitions.has(token)) continue

    violations.push({
      filePath,
      line: lineNumber(source, match.index ?? 0),
      rule: 'CSS token 必须在 src 内定义后再使用',
      snippet: token
    })
  }
}

function findLegacyActionBarViolations(violations, source, filePath) {
  addRegexViolations(
    violations,
    source,
    filePath,
    '底部固定表单操作栏必须使用 FormActionBar 组件',
    /\b(?:quick-submit-bar|quick-submit-spacer|detail-edit-actions|detail-edit-spacer)\b/g
  )
}

function findLegacyBottomSheetViolations(violations, source, filePath) {
  const legacyClassRegex = /(?<![-\w])(?:category-form-popup|payment-form-popup|platform-form-popup|picker-popup|icon-popup)(?![-\w])/g
  addRegexViolations(violations, source, filePath, '管理类底部弹窗必须使用 BottomSheet 组件', legacyClassRegex)
}

function findStyleTokenViolations(source, filePath, definitions) {
  const violations = []

  addRegexViolations(
    violations,
    source,
    filePath,
    '禁止在 .vue 中使用裸十六进制色值',
    /#[0-9a-f]{3,8}\b/gim
  )
  addRegexViolations(
    violations,
    source,
    filePath,
    '禁止在 .vue 中使用固定 rgb/rgba/hsl/hsla 色值',
    /\b(?:rgb|rgba|hsl|hsla)\(\s*(?!var\()[^)]+\)/gim
  )
  findDeclarationValueViolations(
    violations,
    source,
    filePath,
    'font-size',
    'font-size 必须使用字号 token 或 calc(token)',
    (value) => /\b\d+(?:\.\d+)?(?:px|rem|em|vw|vh|%)\b/i.test(value) &&
      !value.startsWith('var(') &&
      !value.startsWith('calc(')
  )
  findDeclarationValueViolations(
    violations,
    source,
    filePath,
    'border-radius',
    'border-radius 必须使用圆角 token，0 值组合除外',
    (value) => /\b(?!0(?:px|rem|em|%)?\b)\d+(?:\.\d+)?(?:px|rem|em|%)\b/i.test(value)
  )
  findBoxShadowViolations(violations, source, filePath)
  findUndefinedTokenViolations(violations, source, filePath, definitions)
  findLegacyActionBarViolations(violations, source, filePath)
  findLegacyBottomSheetViolations(violations, source, filePath)

  return violations
}

await collectFiles(srcDir)

const definitions = await collectCssVariableDefinitions()
const violations = []

for (const filePath of vueFiles) {
  const source = await readFile(filePath, 'utf8')
  violations.push(...findStyleTokenViolations(source, filePath, definitions))
}

if (violations.length > 0) {
  console.error(`UI token check failed: ${violations.length} violation(s) found.`)
  for (const violation of violations) {
    console.error(`- ${relativePath(violation.filePath)}:${violation.line} ${violation.rule}`)
    console.error(`  ${violation.snippet}`)
  }
  process.exit(1)
}

console.log(`UI token check passed: ${vueFiles.length} Vue files scanned.`)
