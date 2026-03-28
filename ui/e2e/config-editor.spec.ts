import { test, expect } from '@playwright/test'

test.describe('Config Editor', () => {
  test('sidebar shows list of configs', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)
    await expect(page.locator('aside')).toContainText('1.0')
  })

  test('active config shows ACTIVE badge in sidebar', async ({ page }) => {
    await page.goto('/config')
    await expect(page.locator('aside').getByText('ACTIVE')).toBeVisible({ timeout: 15000 })
  })

  test('clicking a config loads its raw YAML in the editor', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(2000) // wait for Monaco

    // Monaco should show actual YAML content, not a serialized string
    const monacoText = await page.locator('.monaco-editor').innerText()
    expect(monacoText).toContain('classification')
    expect(monacoText).toContain('ACQUIRING')
  })

  test('version input is populated when config is selected', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(500)

    const versionInput = page.locator('input[type="text"]').first()
    await expect(versionInput).toHaveValue('1.0')
  })

  test('tabs are visible after selecting a config', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(500)

    await expect(page.getByRole('button', { name: 'Raw YAML' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Classification' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'State Machines' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Field Mappings' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Fee Types' })).toBeVisible()
  })

  test('Classification tab shows family rules with ACQUIRING', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(500)

    const tabBar = page.locator('div.flex.border-b.border-zinc-200')
    await tabBar.getByText('Classification').click()
    await page.waitForTimeout(500)

    // Classification tab should render the family rules table with ACQUIRING as the default rule
    await expect(page.getByText('Family Classification Rules')).toBeVisible()
    await expect(page.getByText('ACQUIRING')).toBeVisible()
    // Should show the rule type badges
    await expect(page.getByText('default')).toBeVisible()
  })

  test('Classification tab shows multiple rules from real config', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    // Find and click the 1.0 config specifically (has ACTIVE badge)
    await page.locator('aside button').filter({ hasText: 'ACTIVE' }).click()
    await page.waitForTimeout(500)

    const tabBar = page.locator('div.flex.border-b.border-zinc-200')
    await tabBar.getByText('Classification').click()
    await page.waitForTimeout(800)

    // The real 1.0 config has 4 rules: CASH, PAYOUT, TOPUP, ACQUIRING
    const rows = page.locator('table tbody tr')
    const count = await rows.count()
    expect(count).toBeGreaterThanOrEqual(4)

    // Should show multiple family name badges (ACQUIRING is default, others are results)
    const badges = page.locator('span.bg-blue-50')
    const badgeCount = await badges.count()
    expect(badgeCount).toBeGreaterThanOrEqual(4)
  })

  test('State Machines tab shows family selector buttons', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(500)

    const tabBar = page.locator('div.flex.border-b.border-zinc-200')
    await tabBar.getByText('State Machines').click()
    await page.waitForTimeout(1000)

    // State Machines tab shows family selector buttons for each state machine
    await expect(page.getByText('Trade Family')).toBeVisible()
    // Real 1.0 config has ACQUIRING, PAYOUT, TOPUP, CASH state machines
    await expect(page.getByRole('button', { name: 'ACQUIRING' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'CASH' })).toBeVisible()
  })

  test('State Machines tab shows ReactFlow canvas', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(500)

    const tabBar = page.locator('div.flex.border-b.border-zinc-200')
    await tabBar.getByText('State Machines').click()
    await page.waitForTimeout(1500)

    // ReactFlow canvas should be rendered
    await expect(page.locator('.react-flow')).toBeVisible()
  })

  test('Field Mappings tab shows mappings table', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside button').filter({ hasText: 'ACTIVE' }).click()
    await page.waitForTimeout(500)

    const tabBar = page.locator('div.flex.border-b.border-zinc-200')
    await tabBar.getByText('Field Mappings').click()
    await page.waitForTimeout(800)

    // The real 1.0 config has many field mappings — table should show rows
    const body = await page.locator('body').innerText()
    expect(body).not.toContain('Cannot read')
    // Should show field mapping keys (entity_id, processed_at, etc.)
    expect(body).toContain('entity_id')
  })

  test('Validate button calls API and shows Config is valid', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: 'Validate' }).click()
    await page.waitForTimeout(3000)

    await expect(page.getByText('Config is valid').first()).toBeVisible()
  })

  test('New Config button shows empty editor with New badge', async ({ page }) => {
    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: '+ New Config' }).click()
    await page.waitForTimeout(500)

    await expect(page.locator('span').filter({ hasText: /^New$/ })).toBeVisible()
    const versionInput = page.locator('input[type="text"]').first()
    await expect(versionInput).toHaveValue('1.0-draft')
  })

  test('save creates new config that persists to API', async ({ page }) => {
    const ts = Date.now()
    const version = `e2e-test-${ts}`

    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: '+ New Config' }).click()
    await page.waitForTimeout(300)

    // Set a unique version
    const versionInput = page.locator('input[type="text"]').first()
    await versionInput.fill(version)

    await page.getByRole('button', { name: 'Save' }).click()
    await page.waitForTimeout(2000)

    // Toast should say saved
    const body = await page.locator('body').innerText()
    expect(body).toContain('saved successfully')

    // Verify it was saved to the API
    const resp = await page.request.get('/api/configs')
    const configs = await resp.json()
    const saved = configs.find((c: { version: string }) => c.version === version)
    expect(saved).toBeTruthy()
    expect(saved.content).toContain('ACQUIRING') // content is YAML string
  })

  test('validate sends YAML string (not JSON object) to API', async ({ page }) => {
    // Intercept the validate request to confirm content is sent as a string
    const requests: string[] = []
    page.on('request', (req) => {
      if (req.url().includes('/configs/validate') && req.method() === 'POST') {
        requests.push(req.postData() ?? '')
      }
    })

    await page.goto('/config')
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(1000)

    await page.locator('aside').getByText('1.0').first().click()
    await page.waitForTimeout(1000)

    await page.getByRole('button', { name: 'Validate' }).click()
    await page.waitForTimeout(2000)

    expect(requests.length).toBeGreaterThan(0)
    const body = JSON.parse(requests[0])
    // content should be a YAML string, not an object
    expect(typeof body.content).toBe('string')
    expect(body.content).toContain('classification')
  })

  test('can validate via API with YAML string content', async ({ page }) => {
    const response = await page.request.post('/api/configs/validate', {
      data: { content: 'version: "1.0"\nclassification:\n  family:\n    - default: ACQUIRING\n  type:\n    priority: []\n    field_per_source: {}\n    mapping: {}\nstate_machines: {}\nfield_mappings: {}\nfee_type_mappings: {}\n' }
    })
    expect(response.status()).toBe(200)
    const body = await response.json()
    expect(body.valid).toBe(true)
  })
})
