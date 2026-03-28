import { test, expect } from '@playwright/test'

test.describe('Navigation', () => {
  test('nav bar is visible on all pages', async ({ page }) => {
    for (const path of ['/', '/dlq', '/config', '/test-bench', '/live']) {
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(500)
      await expect(page.locator('nav')).toBeVisible({ timeout: 5000 })
      // Brand name is a span inside the nav logo area
      await expect(page.locator('nav').locator('span').filter({ hasText: 'Nexus' })).toBeVisible()
    }
  })

  test('all nav links are present', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'DLQ' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Config' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Test Bench' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Live' })).toBeVisible()
  })

  test('clicking nav links navigates correctly', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle')

    await page.getByRole('link', { name: 'DLQ' }).click()
    await expect(page).toHaveURL(/\/dlq/)
    await expect(page.getByText('Dead Letter Queue')).toBeVisible()

    await page.getByRole('link', { name: 'Config' }).click()
    await expect(page).toHaveURL(/\/config/)

    await page.getByRole('link', { name: 'Test Bench' }).click()
    await expect(page).toHaveURL(/\/test-bench/)
    await expect(page.getByRole('heading', { name: 'Test Bench' })).toBeVisible()

    await page.getByRole('link', { name: 'Live' }).click()
    await expect(page).toHaveURL(/\/live/)
    await expect(page.getByText('Live Stream')).toBeVisible()

    await page.getByRole('link', { name: 'Dashboard' }).click()
    await expect(page).toHaveURL('/')
  })

  test('active nav link is highlighted', async ({ page }) => {
    await page.goto('/dlq')
    await page.waitForLoadState('networkidle')

    // The active link should have a different style class
    const dlqLink = page.getByRole('link', { name: 'DLQ' })
    const className = await dlqLink.getAttribute('class')
    expect(className).toContain('text-zinc-900') // active style
  })
})
