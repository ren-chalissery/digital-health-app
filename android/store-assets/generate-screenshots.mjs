/**
 * Renders Play Store screenshots from static HTML mockups.
 *
 *   cd android/store-assets
 *   npx playwright@1.55.0 install chromium   # first run only
 *   npx playwright@1.55.0 exec node generate-screenshots.mjs
 */
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { chromium } from 'playwright';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const mockupsDir = path.join(__dirname, 'mockups');
const outDir = path.join(__dirname, 'screenshots');

const phoneScreens = [
  { html: 'sign-in.html', png: '01-sign-in.png', width: 1080, height: 1920 },
  { html: 'dashboard.html', png: '02-dashboard.png', width: 1080, height: 1920 },
  { html: 'learn.html', png: '03-learn-assistant.png', width: 1080, height: 1920 },
  { html: 'module.html', png: '04-module-video.png', width: 1080, height: 1920 },
  { html: 'reflect.html', png: '05-reflect-journal.png', width: 1080, height: 1920 },
];

const featureGraphic = {
  html: 'feature-graphic.html',
  png: 'feature-graphic-1024x500.png',
  width: 1024,
  height: 500,
};

await mkdir(outDir, { recursive: true });

const browser = await chromium.launch();

try {
  for (const screen of [...phoneScreens, featureGraphic]) {
    const page = await browser.newPage({
      viewport: { width: screen.width, height: screen.height },
      deviceScaleFactor: 1,
    });
    const fileUrl = pathToFileURL(path.join(mockupsDir, screen.html)).href;
    await page.goto(fileUrl, { waitUntil: 'networkidle' });
    await page.screenshot({
      path: path.join(outDir, screen.png),
      type: 'png',
    });
    await page.close();
    console.log(`Wrote ${screen.png}`);
  }
} finally {
  await browser.close();
}

console.log(`\nScreenshots saved to ${outDir}`);
