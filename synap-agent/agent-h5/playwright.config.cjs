const { defineConfig, devices } = require('@playwright/test');
const chromiumLaunchOptions = process.env.PW_EXECUTABLE_PATH
  ? { executablePath: process.env.PW_EXECUTABLE_PATH }
  : {};

module.exports = defineConfig({
  testDir: './test/e2e',
  timeout: 30000,
  fullyParallel: false,
  webServer: {
    command: 'node test/e2e/server.cjs',
    port: 4173,
    reuseExistingServer: false
  },
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure'
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], launchOptions: chromiumLaunchOptions } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } }
  ]
});
