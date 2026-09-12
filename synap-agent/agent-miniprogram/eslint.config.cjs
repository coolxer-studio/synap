const js = require('@eslint/js');

module.exports = [
  { ignores: ['dist/**', 'demo/wechat/agent.js'] },
  {
    files: ['src/**/*.js', 'config/*.js', 'test/*.cjs'],
    languageOptions: {
      ecmaVersion: 2021,
      sourceType: 'commonjs',
      globals: {
        wx: 'readonly',
        App: 'readonly',
        Page: 'readonly',
        getCurrentPages: 'readonly',
        require: 'readonly',
        module: 'readonly',
        __dirname: 'readonly',
        process: 'readonly',
        Buffer: 'readonly',
        console: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        queueMicrotask: 'readonly',
        performance: 'readonly',
        TextEncoder: 'readonly',
        Uint8Array: 'readonly',
        unescape: 'readonly',
        encodeURIComponent: 'readonly',
        global: 'readonly'
      }
    },
    rules: Object.assign({}, js.configs.recommended.rules, {
      'no-console': 'off',
      'no-prototype-builtins': 'off',
      'no-unused-vars': ['error', {
        argsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_'
      }]
    })
  }
];
