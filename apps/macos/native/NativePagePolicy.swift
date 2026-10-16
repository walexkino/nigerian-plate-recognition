import Foundation

/// An allowlist for the privileged native bridge and main-page navigation.
struct NativePagePolicy {
    let page: URL
    func accepts(_ url: URL?, mainFrame: Bool) -> Bool {
        guard mainFrame, let url = url, url.isFileURL, url.host == nil || url.host == "",
              url.query == nil, url.fragment == nil else { return false }
        return url.standardizedFileURL == page.standardizedFileURL
    }
}
