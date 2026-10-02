import Foundation
import ImageIO
import UniformTypeIdentifiers

// 위젯 확장과 알림 서비스 확장이 함께 쓰는 코드.
// 앱(React Native)이 App Group 저장소에 넣어 준 토큰으로 서버에서 최신 사진을 받아, 공유 컨테이너에 캐시한다.

enum SharedConfig {
    /// 앱 쪽 WIDGET_KIND (src/lib/config.ts) 와 같아야 한다.
    static let widgetKind = "MomentWidget"

    /// app.config.js 의 APP_GROUP 과 같은 규칙: "group.<앱 번들 ID>".
    /// 확장의 번들 ID는 "<앱 번들 ID>.<확장 이름>" 이라서 마지막 조각을 떼면 앱 번들 ID가 된다.
    static let appGroup: String = {
        let id = Bundle.main.bundleIdentifier ?? ""
        let isExtension = Bundle.main.bundleURL.pathExtension == "appex"
        let appId = isExtension ? id.split(separator: ".").dropLast().joined(separator: ".") : id
        return "group.\(appId)"
    }()
}

/// 키 이름은 src/lib/widgetBridge.ts 와 같아야 한다.
enum SharedStore {
    static var defaults: UserDefaults? { UserDefaults(suiteName: SharedConfig.appGroup) }

    static var accessToken: String? { defaults?.string(forKey: "accessToken") }
    static var apiBaseUrl: URL? { defaults?.string(forKey: "apiBaseUrl").flatMap(URL.init(string:)) }
    static var userId: Int? {
        let id = defaults?.integer(forKey: "userId") ?? 0
        return id == 0 ? nil : id
    }
    static var apnsEnvironment: String { defaults?.string(forKey: "apnsEnvironment") ?? "development" }

    static var widgetPushToken: String? {
        get { defaults?.string(forKey: "widgetPushToken") }
        set { defaults?.set(newValue, forKey: "widgetPushToken") }
    }

    static func markWidgetPushTokenRegistered(_ token: String) {
        defaults?.set(token, forKey: "widgetPushTokenRegistered")
    }

    static var cacheDirectory: URL? {
        FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: SharedConfig.appGroup)?
            .appendingPathComponent("widget", isDirectory: true)
    }
}

struct ReactionCount: Codable, Hashable {
    let emoji: String
    let count: Int
}

struct CommentLine: Codable, Hashable {
    let author: String
    let text: String
}

/// 위젯이 그리는 데 필요한 전부. 공유 컨테이너에 JSON 으로 저장한다.
struct WidgetSnapshot: Codable {
    let etag: String?
    let userId: Int
    let momentId: Int
    let senderId: Int
    let senderName: String
    let createdAt: Date
    let reactions: [ReactionCount]
    let reactionCount: Int
    let comments: [CommentLine]
    let commentCount: Int
    let imageFile: String
}

/// GET /api/widget/latest 응답 (docs/api.md 의 WidgetLatest)
private struct WidgetLatestResponse: Decodable {
    struct Sender: Decodable {
        let id: Int
        let displayName: String
    }

    struct Moment: Decodable {
        let id: Int
        let sender: Sender
        let createdAt: Date
        let thumbUrl: String
    }

    let version: String
    let moment: Moment
    let reactions: [ReactionCount]
    let reactionCount: Int
    let comments: [CommentLine]
    let commentCount: Int
}

enum WidgetCache {
    private static let snapshotName = "snapshot.json"

    static func load() -> WidgetSnapshot? {
        guard let url = SharedStore.cacheDirectory?.appendingPathComponent(snapshotName),
              let data = try? Data(contentsOf: url)
        else { return nil }
        return try? JSONDecoder().decode(WidgetSnapshot.self, from: data)
    }

    static func save(_ snapshot: WidgetSnapshot) throws {
        let dir = try ensureDirectory()
        try JSONEncoder().encode(snapshot).write(to: dir.appendingPathComponent(snapshotName), options: .atomic)
    }

    static func imageURL(_ name: String) -> URL? {
        SharedStore.cacheDirectory?.appendingPathComponent(name)
    }

    static func imageExists(_ name: String) -> Bool {
        guard let url = imageURL(name) else { return false }
        return FileManager.default.fileExists(atPath: url.path)
    }

    static func writeImage(_ data: Data, name: String) throws {
        try data.write(to: try ensureDirectory().appendingPathComponent(name), options: .atomic)
    }

    /// 지난 사진 파일을 지운다. 다른 확장이 방금 쓴 파일은 남겨 두려고 1분 이내 파일은 건드리지 않는다.
    static func removeImages(except keep: String) {
        guard let dir = SharedStore.cacheDirectory,
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])
        else { return }
        let cutoff = Date().addingTimeInterval(-60)
        for file in files where file.pathExtension == "jpg" && file.lastPathComponent != keep {
            let modified = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate ?? .distantPast
            if modified < cutoff { try? FileManager.default.removeItem(at: file) }
        }
    }

    static func clear() {
        guard let dir = SharedStore.cacheDirectory else { return }
        try? FileManager.default.removeItem(at: dir)
    }

    private static func ensureDirectory() throws -> URL {
        guard let dir = SharedStore.cacheDirectory else { throw CocoaError(.fileNoSuchFile) }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}

enum SyncResult {
    case updated, unchanged, empty, signedOut, failed
}

/// 누가 받으러 갔는지. 서버가 「사진이 찍히고 몇 초 뒤 이 폰에 닿았는지」를 경로별로 기록한다 (X-Widget-Source).
enum SyncSource: String {
    case notification // 알림 서비스 확장 (사진 알림을 받은 순간)
    case widget // 위젯 타임라인 (위젯 푸시 · 15분 예약 · 앱이 요청)
}

enum WidgetSync {
    /// 서버에서 최신 위젯 데이터를 받아 캐시를 갱신한다. 바뀐 게 없으면 304 로 빠르게 끝난다.
    static func refresh(timeout: TimeInterval, source: SyncSource) async -> SyncResult {
        guard let token = SharedStore.accessToken,
              let base = SharedStore.apiBaseUrl,
              let userId = SharedStore.userId
        else {
            WidgetCache.clear()
            return .signedOut
        }

        let session = makeSession(timeout: timeout)
        var request = URLRequest(url: base.appendingPathComponent("api/widget/latest"))
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(source.rawValue, forHTTPHeaderField: "X-Widget-Source")
        let cached = WidgetCache.load().flatMap { $0.userId == userId ? $0 : nil }
        if let etag = cached?.etag, let cached, WidgetCache.imageExists(cached.imageFile) {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return .failed }
            switch http.statusCode {
            case 200: break
            case 304: return .unchanged
            case 204:
                WidgetCache.clear()
                return .empty
            case 401:
                WidgetCache.clear()
                return .signedOut
            default: return .failed
            }

            let latest = try makeDecoder().decode(WidgetLatestResponse.self, from: data)
            let imageFile = "moment-\(latest.moment.id).jpg"
            // 댓글·반응만 바뀐 경우에는 사진을 다시 받지 않는다.
            if !WidgetCache.imageExists(imageFile) {
                guard let url = URL(string: latest.moment.thumbUrl, relativeTo: base)?.absoluteURL else { return .failed }
                let (imageData, imageResponse) = try await session.data(from: url)
                guard (imageResponse as? HTTPURLResponse)?.statusCode == 200,
                      let jpeg = ImageDownsampler.jpeg(from: imageData, maxPixel: 600)
                else { return .failed }
                try WidgetCache.writeImage(jpeg, name: imageFile)
            }

            try WidgetCache.save(WidgetSnapshot(
                etag: http.value(forHTTPHeaderField: "ETag") ?? "\"\(latest.version)\"",
                userId: userId,
                momentId: latest.moment.id,
                senderId: latest.moment.sender.id,
                senderName: latest.moment.sender.displayName,
                createdAt: latest.moment.createdAt,
                reactions: latest.reactions,
                reactionCount: latest.reactionCount,
                comments: latest.comments,
                commentCount: latest.commentCount,
                imageFile: imageFile
            ))
            WidgetCache.removeImages(except: imageFile)
            return .updated
        } catch {
            return .failed
        }
    }

    static func makeSession(timeout: TimeInterval) -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = timeout
        config.timeoutIntervalForResource = timeout * 2
        config.waitsForConnectivity = false
        return URLSession(configuration: config)
    }

    private static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let text = try decoder.singleValueContainer().decode(String.self)
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: text) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: text) { return date }
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "날짜 형식이 아니에요: \(text)"))
        }
        return decoder
    }
}

enum WidgetAPI {
    /// iOS 26 위젯 푸시 토큰을 서버에 등록한다 (PUT /api/devices, kind = widget).
    static func registerWidgetToken(_ token: String) async -> Bool {
        guard let request = makeRequest(path: "api/devices", method: "PUT", body: [
            "token": token,
            "kind": "widget",
            "environment": SharedStore.apnsEnvironment,
        ]) else { return false }
        let response = try? await WidgetSync.makeSession(timeout: 10).data(for: request).1
        return (response as? HTTPURLResponse)?.statusCode == 204
    }

    static func unregister(token: String) async {
        guard let request = makeRequest(path: "api/devices/\(token)", method: "DELETE", body: nil) else { return }
        _ = try? await WidgetSync.makeSession(timeout: 10).data(for: request)
    }

    private static func makeRequest(path: String, method: String, body: [String: String]?) -> URLRequest? {
        guard let token = SharedStore.accessToken, let base = SharedStore.apiBaseUrl else { return nil }
        var request = URLRequest(url: base.appendingPathComponent(path))
        request.httpMethod = method
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        return request
    }
}

enum ImageDownsampler {
    /// 위젯은 메모리 한도가 작아서, 원본을 그대로 쓰지 않고 작게 줄여 저장한다.
    static func jpeg(from data: Data, maxPixel: Int) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixel,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        let output = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(output, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: 0.85] as CFDictionary)
        return CGImageDestinationFinalize(destination) ? output as Data : nil
    }
}
