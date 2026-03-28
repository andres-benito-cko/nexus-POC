import { test, expect } from '@playwright/test'

test.describe('Live Screen', () => {
  test('page loads without errors', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1500)
    const content = await page.locator('body').innerText()
    expect(content).not.toContain('Cannot read')
    expect(content).not.toContain('is not defined')
  })

  test('shows Live Stream heading and panel headers', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await expect(page.getByText('Live Stream')).toBeVisible()
    await expect(page.locator('h3').filter({ hasText: 'Event Stream' })).toBeVisible()
    await expect(page.locator('h3').filter({ hasText: 'Event Detail' })).toBeVisible()
  })

  test('shows Connect/Disconnect button and Disconnected indicator', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)
    await expect(page.getByRole('button', { name: 'Connect' })).toBeVisible()
    await expect(page.getByText('Disconnected').first()).toBeVisible()
  })

  test('scenarios dropdown loads with real options', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    const select = page.locator('select')
    await expect(select).toBeVisible()
    const options = select.locator('option')
    const count = await options.count()
    expect(count).toBeGreaterThan(1)

    // First option should be a real scenario (id: 01 - Acquiring Capture)
    const firstOption = await options.first().textContent()
    expect(firstOption).toContain('01')
  })

  test('Play and Stop buttons are visible', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)
    await expect(page.getByRole('button', { name: 'Play' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Stop' })).toBeVisible()
  })

  test('Connect button connects WebSocket and shows Connected', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: 'Connect' }).click()
    await expect(page.getByText('Connected').first()).toBeVisible({ timeout: 5000 })
    await expect(page.getByRole('button', { name: 'Disconnect' })).toBeVisible()
  })

  test('playing a scenario sends NEXUS events to stream', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    await page.getByRole('button', { name: 'Connect' }).click()
    await expect(page.getByText('Connected').first()).toBeVisible({ timeout: 5000 })

    await page.locator('select').selectOption({ index: 0 })
    await page.getByRole('button', { name: 'Play' }).click()

    // Events arrive via Kafka pipeline — wait up to 30s
    const firstCard = page.locator('div.divide-y > div').first()
    await firstCard.waitFor({ timeout: 30000 })

    // The event card should show the NEXUS badge and an action_id
    await expect(page.getByText('NEXUS').first()).toBeVisible()
  })

  test('event card shows action_id (not --)', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    await page.getByRole('button', { name: 'Connect' }).click()
    await expect(page.getByText('Connected').first()).toBeVisible({ timeout: 5000 })

    await page.locator('select').selectOption({ index: 0 })
    await page.getByRole('button', { name: 'Play' }).click()

    const firstCard = page.locator('div.divide-y > div').first()
    await firstCard.waitFor({ timeout: 30000 })

    // Event card should show an action_id (not '--' which means field was not found)
    const cardText = await firstCard.innerText()
    expect(cardText).not.toContain('--')
    // Should contain something that looks like an action ID
    expect(cardText.length).toBeGreaterThan(5)
  })

  test('clicking an event shows transaction detail with real fields', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    await page.getByRole('button', { name: 'Connect' }).click()
    await expect(page.getByText('Connected').first()).toBeVisible({ timeout: 5000 })

    await page.locator('select').selectOption({ index: 0 })
    await page.getByRole('button', { name: 'Play' }).click()

    const firstCard = page.locator('div.divide-y > div').first()
    await firstCard.waitFor({ timeout: 30000 })
    await firstCard.click()
    await page.waitForTimeout(500)

    // Detail panel should show the NEXUS_TRANSACTION label
    await expect(page.getByText('NEXUS_TRANSACTION')).toBeVisible()

    // Transaction section should show "Transaction" header with status
    await expect(page.getByText('Transaction').first()).toBeVisible()
  })

  test('transaction trace in detail shows ACQUIRING/CAPTURE trade', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    await page.getByRole('button', { name: 'Connect' }).click()
    await expect(page.getByText('Connected').first()).toBeVisible({ timeout: 5000 })

    await page.locator('select').selectOption({ index: 0 })
    await page.getByRole('button', { name: 'Play' }).click()

    const firstCard = page.locator('div.divide-y > div').first()
    await firstCard.waitFor({ timeout: 30000 })
    await firstCard.click()
    await page.waitForTimeout(500)

    // Trades section should show ACQUIRING / CAPTURE (not UNKNOWN / UNKNOWN)
    const detailPanel = page.locator('.lg\\:col-span-3')
    const detailText = await detailPanel.innerText()
    expect(detailText).toContain('ACQUIRING')
  })

  test('Clear button removes all events', async ({ page }) => {
    await page.goto('/live')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)

    await page.getByRole('button', { name: 'Connect' }).click()
    await expect(page.getByText('Connected').first()).toBeVisible({ timeout: 5000 })

    await page.locator('select').selectOption({ index: 0 })
    await page.getByRole('button', { name: 'Play' }).click()

    await page.locator('div.divide-y > div').first().waitFor({ timeout: 30000 })

    // Stop first so no new events arrive immediately after clear
    await page.getByRole('button', { name: 'Stop' }).click()
    await page.waitForTimeout(1000)
    await page.getByRole('button', { name: 'Clear' }).click()
    await page.waitForTimeout(500)

    await expect(page.locator('p').filter({ hasText: /Waiting for events|Connect to start/ })).toBeVisible()
  })

  test('simulator API returns real scenarios', async ({ page }) => {
    const response = await page.request.get('/simulate/scenarios')
    expect(response.status()).toBe(200)
    const scenarios = await response.json()
    expect(Array.isArray(scenarios)).toBe(true)
    expect(scenarios.length).toBeGreaterThan(0)
    expect(scenarios[0].id).toBeTruthy()
    expect(scenarios[0].name).toBeTruthy()
  })
})
