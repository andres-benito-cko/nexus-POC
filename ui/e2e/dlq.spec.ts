import { test, expect } from '@playwright/test'

test.describe('DLQ Page', () => {
  test('shows Dead Letter Queue heading', async ({ page }) => {
    await page.goto('/dlq')
    await page.waitForLoadState('networkidle')
    await expect(page.getByText('Dead Letter Queue')).toBeVisible()
  })

  test('shows events count in header', async ({ page }) => {
    await page.goto('/dlq')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)
    // Should show "N events" label
    await expect(page.getByText(/\d+ events/)).toBeVisible()
  })

  test('shows empty state or event table', async ({ page }) => {
    await page.goto('/dlq')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    const emptyMsg = page.getByText('No DLQ events')
    const table = page.locator('table')

    const hasEmpty = await emptyMsg.isVisible()
    const hasTable = await table.isVisible()

    // One of them must be true
    expect(hasEmpty || hasTable).toBe(true)
  })

  test('table headers shown when there are events', async ({ page }) => {
    const apiResp = await page.request.get('/api/dlq')
    const events = await apiResp.json()

    if (events.length > 0) {
      await page.goto('/dlq')
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(1000)

      await expect(page.getByText('Action ID')).toBeVisible()
      await expect(page.getByText('Created At')).toBeVisible()
      await expect(page.getByText('Errors')).toBeVisible()
    }
  })

  test('API returns DLQ events list', async ({ page }) => {
    const response = await page.request.get('/api/dlq')
    expect(response.status()).toBe(200)
    const events = await response.json()
    expect(Array.isArray(events)).toBe(true)
  })

  test('no JS errors on page', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await page.goto('/dlq')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)
    const critical = errors.filter(e => !e.includes('ResizeObserver') && !e.includes('Non-Error'))
    expect(critical).toHaveLength(0)
  })
})
