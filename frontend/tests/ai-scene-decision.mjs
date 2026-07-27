import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const frontendDirectory = fileURLToPath(new URL('..', import.meta.url))
const tsconfigPath = fileURLToPath(new URL('../tsconfig.json', import.meta.url))
const nullableContractPath = fileURLToPath(
  new URL('./ai-scene-nullable-contract.ts', import.meta.url)
)
const nullableContractSource = `
import type { AiSceneRecommendation } from '../src/types'

const uncertain: AiSceneRecommendation = {
  status: 'UNCERTAIN',
  categoryId: null,
  categoryName: null,
  channel: null,
  onlinePlatformId: null,
  onlinePlatformName: null,
  confidence: 0,
  reason: '无法确定'
}

void uncertain
`
const configFile = ts.readConfigFile(tsconfigPath, ts.sys.readFile)
assert.equal(configFile.error, undefined)
const parsedConfig = ts.parseJsonConfigFileContent(
  configFile.config,
  ts.sys,
  frontendDirectory
)
const compilerHost = ts.createCompilerHost(parsedConfig.options)
const defaultGetSourceFile = compilerHost.getSourceFile.bind(compilerHost)

compilerHost.fileExists = (fileName) =>
  fileName === nullableContractPath || ts.sys.fileExists(fileName)
compilerHost.readFile = (fileName) =>
  fileName === nullableContractPath
    ? nullableContractSource
    : ts.sys.readFile(fileName)
compilerHost.getSourceFile = (
  fileName,
  languageVersion,
  onError,
  shouldCreateNewSourceFile
) =>
  fileName === nullableContractPath
    ? ts.createSourceFile(
        fileName,
        nullableContractSource,
        languageVersion,
        true
      )
    : defaultGetSourceFile(
        fileName,
        languageVersion,
        onError,
        shouldCreateNewSourceFile
      )

const nullableContractProgram = ts.createProgram(
  [nullableContractPath],
  parsedConfig.options,
  compilerHost
)
const nullableContractErrors = ts
  .getPreEmitDiagnostics(nullableContractProgram)
  .filter((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
  .map((diagnostic) => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'))

assert.deepEqual(nullableContractErrors, [])

const source = await readFile(
  new URL('../src/utils/aiSceneDecision.ts', import.meta.url),
  'utf8'
)
const { outputText } = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022
  }
})
const moduleUrl =
  `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`
const { resolveAiSceneDecision } = await import(moduleUrl)

const history = {
  categoryId: 20,
  channel: 'ONLINE',
  onlinePlatformId: 30
}
const ai = {
  status: 'SUGGESTED',
  categoryId: 20,
  categoryName: '交通',
  channel: 'ONLINE',
  onlinePlatformId: 30,
  onlinePlatformName: '滴滴',
  confidence: 0.91,
  reason: '与历史场景匹配'
}
const uncertainAi = {
  status: 'UNCERTAIN',
  categoryId: null,
  categoryName: null,
  channel: null,
  onlinePlatformId: null,
  onlinePlatformName: null,
  confidence: 0,
  reason: '无法确定'
}

assert.deepEqual(resolveAiSceneDecision(null, ai), {
  kind: 'AUTO_APPLY',
  ai
})
assert.deepEqual(resolveAiSceneDecision(history, ai), {
  kind: 'AGREEMENT',
  ai
})
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, categoryId: 21 }),
  { kind: 'CONFLICT', ai: { ...ai, categoryId: 21 } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, channel: 'OFFLINE' }),
  { kind: 'CONFLICT', ai: { ...ai, channel: 'OFFLINE' } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, onlinePlatformId: 31 }),
  { kind: 'CONFLICT', ai: { ...ai, onlinePlatformId: 31 } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, { ...ai, onlinePlatformId: null }),
  { kind: 'AGREEMENT', ai: { ...ai, onlinePlatformId: null } }
)
assert.deepEqual(
  resolveAiSceneDecision(history, uncertainAi),
  { kind: 'UNCERTAIN', ai: uncertainAi }
)
assert.deepEqual(resolveAiSceneDecision(null, null), {
  kind: 'HISTORY_ONLY'
})
