import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { fileURLToPath } from 'node:url'

const frontendDirectory = fileURLToPath(new URL('../..', import.meta.url))
const viteEntry = fileURLToPath(
  new URL('../../node_modules/vite/bin/vite.js', import.meta.url)
)
const localBaseUrl = 'http://127.0.0.1:4176'

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

async function waitForServer(child, readOutput) {
  const deadline = Date.now() + 20_000

  while (Date.now() < deadline) {
    if (child.exitCode !== null || child.signalCode !== null) {
      throw new Error(`Vite 测试服务提前退出。\n${readOutput()}`)
    }

    try {
      const response = await fetch(`${localBaseUrl}/`)
      if (response.ok) {
        return
      }
    } catch {
      // Vite 仍在启动，继续轮询。
    }

    await delay(100)
  }

  throw new Error(`等待 Vite 测试服务超时。\n${readOutput()}`)
}

async function terminateChild(child) {
  if (child.exitCode !== null || child.signalCode !== null) {
    return
  }

  const exited = once(child, 'exit').then(() => true)
  child.kill('SIGTERM')

  if (await Promise.race([exited, delay(3_000).then(() => false)])) {
    return
  }

  const forceExited = once(child, 'exit')
  child.kill('SIGKILL')
  await forceExited
}

export async function withViteServer(callback) {
  const externalBaseUrl = process.env.AI_SCENE_UI_BASE_URL?.trim()
  if (externalBaseUrl) {
    return callback(externalBaseUrl)
  }

  let output = ''
  const child = spawn(process.execPath, [
    viteEntry,
    '--host',
    '127.0.0.1',
    '--port',
    '4176',
    '--strictPort'
  ], {
    cwd: frontendDirectory,
    env: process.env,
    stdio: ['ignore', 'pipe', 'pipe']
  })

  child.stdout.on('data', (chunk) => {
    output += chunk.toString()
  })
  child.stderr.on('data', (chunk) => {
    output += chunk.toString()
  })

  try {
    await waitForServer(child, () => output)
    return await callback(localBaseUrl)
  } finally {
    await terminateChild(child)
  }
}
