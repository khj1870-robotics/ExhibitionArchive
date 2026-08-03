let browserPromise = null;

export async function renderPage(url) {
  const { chromium } = await import("playwright");
  browserPromise ??= chromium.launch({ headless: true });
  const browser = await browserPromise;
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    locale: "ko-KR",
    userAgent: "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
  });
  try {
    const page = await context.newPage();
    await page.goto(url, { waitUntil: "domcontentloaded", timeout: 30_000 });
    await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
    await page.waitForTimeout(1_000);
    return await page.content();
  } finally {
    await context.close();
  }
}

export async function closeBrowser() {
  if (!browserPromise) return;
  const browser = await browserPromise;
  browserPromise = null;
  await browser.close();
}
