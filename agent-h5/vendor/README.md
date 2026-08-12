# Build-time SDK package directory

The repository no longer vendors an old security SDK. `synap-build` checks out the
pinned `sdkSourceRef`, builds `@coolxer/secure-communication-js@1.0.0`, and places
the temporary package here only inside the isolated build workspace.

For local release verification, generate the same package with:

```bash
npm pack /path/to/secure-communication/client/javascript --pack-destination ./vendor
```
