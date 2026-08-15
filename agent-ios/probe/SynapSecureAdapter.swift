import Foundation
import SecureCommunication

/// Thin Objective-C adapter; queueing and business retries remain in CTProbe.
@objc public final class SynapSecureAdapter: NSObject {
    private let client: SecureCommunication.SecureClient
    private let enrollmentTokenProvider: (() -> String?)?
    private var enrollmentWasRequested = false

    @objc public init?(
        baseURL: URL,
        appID: String,
        serverKeyID: String,
        trustAnchor: String,
        enrollmentTokenProvider: (() -> String?)?
    ) {
        guard let anchor = Data(base64URL: trustAnchor) else { return nil }
        do {
            client = try SecureCommunication.SecureClient(config: SecureClientConfig(
                baseURL: baseURL,
                appID: appID,
                serverTrustAnchors: [serverKeyID: anchor]))
            self.enrollmentTokenProvider = enrollmentTokenProvider
            super.init()
        } catch {
            return nil
        }
    }

    @objc public func post(
        logicalPath: String,
        code: String,
        body: Data
    ) -> Data? {
        let semaphore = DispatchSemaphore(value: 0)
        var result: Data?
        Task {
            defer { semaphore.signal() }
            do {
                if !enrollmentWasRequested {
                    enrollmentWasRequested = true
                    if let token = enrollmentTokenProvider?(), !token.isEmpty {
                        try await client.enroll(token)
                    }
                }
                let request = try SecureRequest(
                    method: "POST",
                    logicalPath: logicalPath,
                    contentType: "application/json",
                    protectedHeaders: ["code": code],
                    body: body,
                    requestID: UUID().uuidString.lowercased())
                let response = try await client.request(request)
                result = response.body
            } catch {
                await client.closeSession()
            }
        }
        semaphore.wait()
        return result
    }
}

private extension Data {
    init?(base64URL: String) {
        guard base64URL.range(
            of: #"^[A-Za-z0-9_-]+$"#, options: .regularExpression) != nil
        else { return nil }
        var encoded = base64URL.replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        self.init(base64Encoded: encoded)
    }
}
