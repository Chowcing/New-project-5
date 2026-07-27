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

function hasViteReadyOutput(output) {
  return /VITE[^\n]*ready in/.test(output)
}

async function waitForChildReady(child, readStdout, readOutput) {
  if (child.exitCode !== null || child.signalCode !== null) {
    throw new Error(`Vite 测试服务提前退出。\n${readOutput()}`)
  }
  if (hasViteReadyOutput(readStdout())) {
    return
  }

  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      finish(new Error(`等待 Vite 子进程就绪超时。\n${readOutput()}`))
    }, 20_000)

    function cleanup() {
      clearTimeout(timeout)
      child.stdout.off('data', inspectOutput)
      child.stderr.off('data', inspectOutput)
      child.off('error', handleError)
      child.off('exit', handleExit)
    }

    function finish(error) {
      cleanup()
      if (error) {
        reject(error)
      } else {
        resolve()
      }
    }

    function inspectOutput() {
      if (hasViteReadyOutput(readStdout())) {
        finish()
      }
    }

    function handleError(error) {
      finish(new Error(`Vite 测试服务启动失败：${error.message}\n${readOutput()}`))
    }

    function handleExit(code, signal) {
      const reason = signal ? `signal ${signal}` : `code ${code}`
      finish(new Error(`Vite 测试服务提前退出（${reason}）。\n${readOutput()}`))
    }

    child.stdout.on('data', inspectOutput)
    child.stderr.on('data', inspectOutput)
    child.once('error', handleError)
    child.once('exit', handleExit)
    inspectOutput()
  })
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
  let stdout = ''
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
    const text = chunk.toString()
    stdout += text
    output += text
  })
  child.stderr.on('data', (chunk) => {
    output += chunk.toString()
  })

  try {
    await waitForChildReady(child, () => stdout, () => output)
    await waitForServer(child, () => output)
    return await callback(localBaseUrl)
  } finally {
    await terminateChild(child)
  }
}
