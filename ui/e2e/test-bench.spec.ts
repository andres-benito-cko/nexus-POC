import { test, expect } from '@playwright/test'

const LE_EVENT = {
  id: 'playwright-test-001',
  actionId: 'act-playwright-001',
  actionRootId: 'pay-playwright-001',
  transactionVersion: 1,
  gatewayEvents: [{
    eventType: 'payment_captured',
    processedOn: '2024-01-15T10:00:00Z',
    amount: { value: 150.00, currencyCode: 'EUR' },
    acquirerName: 'CKO_UK_LTD',
    acquirerCountry: 'GB'
  }],
  balancesChangedEvents: [],
  cosEvents: [],
  schemeSettlementEvents: [],
  cashEvents: []
}

test.describe('Test Bench', () => {
  test('API returns successful transformation with correct fields', async ({ page }) => {
    const response = await page.request.post('/api/test-bench', { data: LE_EVENT })
    expect(response.status()).toBe(200)
    const result = await response.json()

    // Basic success
    expect(result.success).toBe(true)
    expect(result.transaction).toBeTruthy()

    // Transaction fields are snake_case
    expect(result.transaction.nexus_id).toBeTruthy()
    expect(result.transaction.status).toBeTruthy()
    expect(result.transaction.processed_at).toBeTruthy()

    // Transactions should be present and have snake_case fields
    expect(result.transaction.transactions).toBeTruthy()
    expect(result.transaction.transactions.length).toBeGreaterThan(0)

    const txn = result.transaction.transactions[0]
    expect(txn.transaction_id).toBeTruthy()
    expect(txn.product_type).toBe('ACQUIRING')
    expect(txn.transaction_type).toBe('CAPTURE')
    expect(txn.transaction_status).toBeTruthy()

    // Legs should have snake_case fields
    expect(txn.legs).toBeTruthy()
    expect(txn.legs.length).toBeGreaterThan(0)

    const leg = txn.legs[0]
    expect(leg.leg_type).toBeTruthy()
    expect(leg.leg_amount).toBeGreaterThan(0)
    expect(leg.leg_currency).toBe('EUR')
    expect(leg.from_party).toBeTruthy()
    expect(leg.from_party.party_type).toBeTruthy()
    expect(leg.to_party).toBeTruthy()
    expect(leg.to_party.party_type).toBeTruthy()
  })

  test('page loads with Monaco editor and Run button', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await expect(page.locator('.monaco-editor').first()).toBeVisible()
    await expect(page.getByRole('button', { name: 'Run' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Reset' })).toBeVisible()
    await expect(page.getByText('Ready to run')).toBeVisible()
  })

  test('Run button submits and shows SUCCESS with transaction details', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await page.getByRole('button', { name: 'Run' }).click()
    await expect(page.getByText('SUCCESS')).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('Nexus Transaction Output')).toBeVisible()
  })

  test('Transaction Trace shows product type and type', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await page.getByRole('button', { name: 'Run' }).click()
    await expect(page.getByText('SUCCESS')).toBeVisible({ timeout: 10000 })

    // Transaction Trace section
    await expect(page.getByText('Transaction Trace')).toBeVisible()

    // Should show "ACQUIRING / CAPTURE" in the transaction section (from product_type / transaction_type)
    await expect(page.getByText('ACQUIRING / CAPTURE')).toBeVisible()
  })

  test('Transaction Trace shows transaction ID', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await page.getByRole('button', { name: 'Run' }).click()
    await expect(page.getByText('SUCCESS')).toBeVisible({ timeout: 10000 })

    // Transaction header should show ID field (exact match, not "Trade ID:")
    await expect(page.getByText('ID:', { exact: true })).toBeVisible()
  })

  test('Transaction Trace shows leg details', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await page.getByRole('button', { name: 'Run' }).click()
    await expect(page.getByText('SUCCESS')).toBeVisible({ timeout: 10000 })

    // Leg should show with type and amount (150.00 EUR)
    await expect(page.getByText('Leg 1')).toBeVisible()
    await expect(page.getByText('150.00 EUR')).toBeVisible()
    // Flow should show party types (not '? → ?')
    const flowText = await page.getByText('Flow:').first().locator('..').innerText()
    expect(flowText).not.toContain('?')
  })

  test('Transaction Trace shows status badge', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await page.getByRole('button', { name: 'Run' }).click()
    await expect(page.getByText('SUCCESS')).toBeVisible({ timeout: 10000 })

    // Trade should show status badge (CAPTURED or SETTLED etc.)
    const traceSection = page.locator('div.glow-border').last()
    const traceText = await traceSection.innerText()
    // Status badge values from the engine
    expect(traceText.match(/CAPTURED|SETTLED|PREDICTED|ACTUAL|NOT_LIVE|LIVE/)).toBeTruthy()
  })

  test('Reset button clears the result', async ({ page }) => {
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2500)

    await page.getByRole('button', { name: 'Run' }).click()
    await expect(page.getByText('SUCCESS')).toBeVisible({ timeout: 10000 })

    await page.getByRole('button', { name: 'Reset' }).click()
    await expect(page.getByText('Ready to run')).toBeVisible()
  })

  test('no JS errors on page', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await page.goto('/test-bench')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(2000)
    const critical = errors.filter(e => !e.includes('ResizeObserver') && !e.includes('Non-Error'))
    expect(critical).toHaveLength(0)
  })
})
