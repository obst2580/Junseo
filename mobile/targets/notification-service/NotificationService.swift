import UserNotifications
import WidgetKit

/// 알림이 화면에 뜨기 직전에 실행된다 (서버가 mutable-content: 1 로 보내기 때문).
/// 1) 서버에서 최신 위젯 데이터를 받아 캐시를 채우고 위젯 갱신을 요청한다.
/// 2) 새 사진 알림이면 그 사진을 알림에 첨부한다.
/// 무음 푸시와 달리 시간당 제한이 없고, 앱이 강제 종료돼 있어도 실행된다.
final class NotificationService: UNNotificationServiceExtension {
    private let lock = NSLock()
    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var content: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        let content = (request.content.mutableCopy() as? UNMutableNotificationContent) ?? UNMutableNotificationContent()
        lock.withLock {
            self.contentHandler = contentHandler
            self.content = content
        }

        let userInfo = request.content.userInfo
        let type = userInfo["type"] as? String
        let momentId = (userInfo["momentId"] as? NSNumber)?.intValue
        let thumbPath = userInfo["thumbUrl"] as? String

        Task {
            if type == "moment" || type == "reaction" || type == "comment" {
                // 확장은 30초 안에 끝나야 해서 여유를 두고 20초까지만 기다린다.
                if await WidgetSync.refresh(timeout: 20, source: .notification) == .updated {
                    WidgetCenter.shared.reloadTimelines(ofKind: SharedConfig.widgetKind)
                }
            }
            if type == "moment", let momentId {
                if let attachment = await Self.attachment(momentId: momentId, fallbackPath: thumbPath) {
                    content.attachments = [attachment]
                }
            }
            deliver()
        }
    }

    override func serviceExtensionTimeWillExpire() {
        deliver()
    }

    /// 한 번만 넘긴다. 시간 초과와 정상 완료가 겹쳐도 안전하게.
    private func deliver() {
        let pending: (((UNNotificationContent) -> Void), UNNotificationContent)? = lock.withLock {
            guard let handler = contentHandler, let content else { return nil }
            contentHandler = nil
            return (handler, content)
        }
        if let pending { pending.0(pending.1) }
    }

    /// 위젯 캐시에 방금 받은 사진이 있으면 그걸 쓰고, 없으면 알림에 실려 온 주소에서 직접 받는다.
    /// 첨부 파일은 시스템이 옮겨 가므로 항상 임시 복사본을 넘긴다.
    private static func attachment(momentId: Int, fallbackPath: String?) async -> UNNotificationAttachment? {
        let name = "moment-\(momentId).jpg"
        let copy = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).jpg")
        do {
            if WidgetCache.imageExists(name), let source = WidgetCache.imageURL(name) {
                try FileManager.default.copyItem(at: source, to: copy)
            } else {
                guard let fallbackPath, let base = SharedStore.apiBaseUrl,
                      let url = URL(string: fallbackPath, relativeTo: base)?.absoluteURL
                else { return nil }
                let (data, response) = try await WidgetSync.makeSession(timeout: 8).data(from: url)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
                try data.write(to: copy)
            }
            return try UNNotificationAttachment(identifier: name, url: copy)
        } catch {
            return nil
        }
    }
}
