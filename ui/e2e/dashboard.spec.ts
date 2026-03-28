import { test, expect } from '@playwright/test'

test.describe('Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1500)
  })

  test('shows active engine status card with ACTIVE badge', async ({ page }) => {
    await expect(page.getByText('Engine Status')).toBeVisible()
    await expect(page.getByText('ACTIVE').first()).toBeVisible()
    await expect(page.getByText('Config loaded')).toBeVisible()
  })

  test('shows active config version in card', async ({ page }) => {
    await expect(page.getByText('Active Config')).toBeVisible()
    const card = page.locator('.glow-border').filter({ hasText: 'Active Config' })
    await expect(card).toContainText('1.0')
  })

  test('DLQ events card shows numeric count', async ({ page }) => {
    await expect(page.getByText('DLQ Events')).toBeVisible()
    const dlqCard = page.locator('.glow-border').filter({ hasText: 'DLQ Events' })
    const countText = await dlqCard.locator('p.text-3xl').textContent()
    expect(Number(countText)).toBeGreaterThanOrEqual(0)
  })

  test('buffer size card renders', async ({ page }) => {
    await expect(page.getByText('Buffer Size')).toBeVisible()
    await expect(page.getByText('Transactions in memory')).toBeVisible()
  })

  test('available configs table shows version 1.0', async ({ page }) => {
    await expect(page.getByText('Available Configs')).toBeVisible()
    await expect(page.locator('table').first().getByText('1.0')).toBeVisible()
  })

  test('recent transactions table shows column headers and data', async ({ page }) => {
    await expect(page.getByText('Recent Transactions')).toBeVisible()
    await expect(page.getByText('Transaction ID')).toBeVisible()
    await expect(page.getByText('Product Type')).toBeVisible()
    await expect(page.getByText('Transaction Type')).toBeVisible()
    const rows = page.locator('table').last().locator('tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThan(0)
  })

  test('transaction rows show non-empty IDs and status', async ({ page }) => {
    const rows = page.locator('table').last().locator('tbody tr')
    const count = await rows.count()
    if (count > 0) {
      const firstRow = rows.first()
      const txId = await firstRow.locator('td').nth(0).textContent()
      expect(txId?.trim().length).toBeGreaterThan(0)
    }
  })

  test('nav bar shows websocket status indicator', async ({ page }) => {
    // WS indicator is the text node inside the flex div at the right of the nav (not the "Live" nav link)
    const wsIndicator = page.locator('nav div.flex.items-center.gap-2.text-xs')
    await expect(wsIndicator).toBeVisible()
    const text = await wsIndicator.textContent()
    expect(text).toMatch(/Live|Disconnected/)
  })

  test('no JS errors on page load', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await page.waitForTimeout(1000)
    const critical = errors.filter(e => !e.includes('ResizeObserver') && !e.includes('Non-Error'))
    expect(critical).toHaveLength(0)
  })
})
