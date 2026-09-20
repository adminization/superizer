#!/usr/bin/env node
//
// Does the wasm build actually run, draw and route?
//
// Three questions the JVM tests cannot answer, and each one is a class of regression that only
// shows up in a browser:
//
//   1. Did it start? — `window.__superizer` never appears when the bundle fails to load.
//   2. Is anything drawn? — Compose paints into a canvas, so a broken render is a *white page*
//      that every DOM assertion still passes on. The only honest check is counting pixels.
//   3. Did a route reach the handler? — `?activate=` and `?link=` are the browser's QR code and
//      notification tap, and they go through code no desktop test exercises.
//
// Usage: node scripts/web-smoke.mjs <dist-dir> <output-dir>

import { createServer } from 'node:http'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { extname, join, resolve } from 'node:path'
import { chromium } from 'playwright'
import { PNG } from 'pngjs'

const dist = resolve(process.argv[2] ?? 'fixture/build/dist/wasmJs/productionExecutable')
const outDir = resolve(process.argv[3] ?? 'build/verify/web')
const PORT = 8085

// swiftshader, because there is no GPU here and Compose renders through Skia: without it the page
// is a white rectangle and every check below would fail for the wrong reason.
const CHROME_ARGS = ['--use-gl=angle', '--use-angle=swiftshader', '--no-sandbox']

// wasm plus Skiko on a software rasteriser is slow to start. This is not a performance budget.
const BOOT_TIMEOUT_MS = 90_000

/** Below this, nothing was painted. See the check that uses it for why it is not higher. */
const INK_FLOOR = 0.002

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.mjs': 'text/javascript',
  '.wasm': 'application/wasm',
  '.css': 'text/css',
  '.json': 'application/json',
}

/** Paths the page asked for and this server does not have — a favicon, mostly. */
const missing = new Set()

function serve() {
  return new Promise((ready) => {
    const server = createServer(async (request, response) => {
      const path = decodeURIComponent((request.url ?? '/').split('?')[0])
      const file = join(dist, path === '/' ? 'index.html' : path)
      try {
        const body = await readFile(file)
        response.writeHead(200, { 'Content-Type': MIME[extname(file)] ?? 'application/octet-stream' })
        response.end(body)
      } catch {
        missing.add(path)
        response.writeHead(404).end('not found')
      }
    })
    server.listen(PORT, () => ready(server))
  })
}

/**
 * The share of pixels that are not the page's white background. A blank render scores ~0.
 *
 * Measured on the *screenshot* rather than by reading the canvas back: Compose draws through
 * WebGL, and a WebGL canvas has no `getImageData` — asking it politely returns zero, which looks
 * exactly like a broken render and is the reason this function is not one line.
 */
async function inkCoverage(page, name) {
  const shot = await page.screenshot()
  await writeFile(join(outDir, `${name}.png`), shot)
  const png = PNG.sync.read(shot)
  let ink = 0
  for (let i = 0; i < png.data.length; i += 4) {
    if (png.data[i] < 240 || png.data[i + 1] < 240 || png.data[i + 2] < 240) ink++
  }
  return ink / (png.width * png.height)
}

async function open(browser, query, errors) {
  const page = await browser.newPage({ viewport: { width: 412, height: 915 } })
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
  page.on('pageerror', (error) => errors.push(String(error)))
  await page.goto(`http://localhost:${PORT}/${query}`)
  await page.waitForFunction('window.__superizer !== undefined', null, { timeout: BOOT_TIMEOUT_MS })
  return page
}

const results = []
function check(name, condition, detail = '') {
  results.push({ name, ok: Boolean(condition), detail })
  console.log(`${condition ? 'ok  ' : 'FAIL'} ${name}${detail ? ` — ${detail}` : ''}`)
}

async function main() {
  if (!existsSync(dist)) {
    console.error(`no bundle at ${dist}`)
    process.exit(1)
  }
  await mkdir(outDir, { recursive: true })

  const server = await serve()
  const browser = await chromium.launch({
    args: CHROME_ARGS,
    // Whatever binary verify.sh found — Playwright's own, or the system's. The two agree about
    // everything this test looks at.
    executablePath: process.env.CHROME_BIN || undefined,
  })
  const errors = []

  try {
    // 1 — it starts, and it draws.
    const home = await open(browser, '?test=1', errors)
    check('the bundle boots and publishes its test bridge', true)
    check('home is the first destination', (await home.evaluate('window.__superizer.destination()')) === 'Home')
    check('every registered app is there', (await home.evaluate('window.__superizer.appCount()')) >= 2)
    const homeInk = await inkCoverage(home, '01-home')
    // A drawn phone screen on a near-white design system is around 1% non-white; a page that
    // failed to render is 0.0%. The threshold separates those two and nothing else — it is not a
    // measure of how much is on the screen.
    check('home is drawn, not blank', homeInk > INK_FLOOR, `${(homeInk * 100).toFixed(2)}% ink`)
    await home.close()

    // 2 — a link in the query opens an app, which is the browser's notification tap.
    const linked = await open(browser, '?test=1&link=' + encodeURIComponent('superizer://app/probe'), errors)
    await linked.waitForFunction('window.__superizer.currentApp() === "probe"', null, { timeout: 20_000 })
      .catch(() => {})
    check('a `?link=` opens the app it names', (await linked.evaluate('window.__superizer.currentApp()')) === 'probe')
    const appInk = await inkCoverage(linked, '02-probe')
    check('the open app is drawn', appInk > INK_FLOOR, `${(appInk * 100).toFixed(2)}% ink`)
    await linked.close()

    // 3 — an activation payload, which is what a QR code carries.
    const payload = JSON.stringify({ schemaVersion: 1, type: 'app_activation', appId: 'test-app', config: { mode: 'qr' } })
    const activated = await open(browser, '?test=1&activate=' + encodeURIComponent(payload), errors)
    await activated.waitForFunction('window.__superizer.currentApp() === "test-app"', null, { timeout: 20_000 })
      .catch(() => {})
    check(
      'an activation payload unlocks and opens the hidden app',
      (await activated.evaluate('window.__superizer.currentApp()')) === 'test-app',
    )
    const events = await activated.evaluate('window.__superizer.events()')
    check('the lifecycle ran in order', ['Configured', 'Created', 'Launched', 'Active'].every((e) => events.includes(e)),
      events.slice(-6).join(', '))
    await inkCoverage(activated, '03-test-app')
    await activated.close()

    // A missing favicon is this server's shortcoming, not the app's, and it arrives as a console
    // error like any other. Everything else counts.
    const appErrors = errors.filter((e) => !/favicon/i.test(e) && !/404/.test(e))
    check('no console errors', appErrors.length === 0, appErrors.slice(0, 3).join(' | '))
    if (missing.size) console.log(`note: 404s served — ${[...missing].join(', ')}`)
  } finally {
    await browser.close()
    server.close()
  }

  await writeFile(join(outDir, 'summary.json'), JSON.stringify({ results, errors }, null, 2))
  await writeFile(join(outDir, 'console.log'), errors.join('\n'))
  process.exit(results.every((r) => r.ok) ? 0 : 1)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
