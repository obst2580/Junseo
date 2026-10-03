import Foundation
import ImageIO
import UniformTypeIdentifiers
import Security

// 위젯 확장과 알림 서비스 확장이 함께 쓰는 코드.
// 공유 Keychain의 로그인 토큰으로 서버에서 최신 사진을 받아, App Group 컨테이너에 캐시한다.

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

    static var accessToken: String? {
        let key = Data("junseo.accessToken".utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "junseo.auth:no-auth",
            kSecAttrAccount as String: key,
            kSecAttrGeneric as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
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

struct CommentLine: Codable, Hashable {
    let author: String
    let text: String
}

/// 앱이 App Group 에 넣어 준 친구 목록 (위젯 편집에서 친구를 고를 때 쓴다)
struct FriendRef: Codable, Hashable {
    let id: Int
    let displayName: String
}

extension SharedStore {
    static var friends: [FriendRef] {
        guard let text = defaults?.string(forKey: "friends"), let data = text.data(using: .utf8) else { return [] }
        return (try? JSONDecoder().decode([FriendRef].self, from: data)) ?? []
    }
}

/// 위젯이 무엇을 보여 주는지: 모든 친구, 또는 한 친구 (홈 화면에서 위젯 편집으로 고른다)
enum FeedKey: Hashable {
    case everyone
    case friend(Int)

    var name: String {
        switch self {
        case .everyone: "all"
        case let .friend(id): "friend-\(id)"
        }
    }

    init?(name: String) {
        if name == "all" {
            self = .everyone
        } else if name.hasPrefix("friend-"), let id = Int(name.dropFirst("friend-".count)) {
            self = .friend(id)
        } else {
            return nil
        }
    }
}

/// 위젯이 넘겨 보는 사진 한 장
struct FeedItem: Codable, Hashable {
    let momentId: Int
    let senderId: Int
    let senderName: String
    let createdAt: Date
    let comments: [CommentLine]
    let commentCount: Int
    let imageFile: String
}

/// 위젯 하나가 넘겨 보는 사진들 (최신 → 오래된 순, 최대 5장). 공유 컨테이너에 JSON 으로 저장한다.
struct FeedSnapshot: Codable {
    let etag: String?
    let userId: Int
    let items: [FeedItem]
}

/// GET /api/widget/feed 응답 (docs/api.md 의 WidgetFeed). 쓰지 않는 필드는 무시한다.
private struct WidgetFeedResponse: Decodable {
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

    struct Item: Decodable {
        let moment: Moment
        let comments: [CommentLine]
        let commentCount: Int
    }

    let version: String
    let items: [Item]
}

enum WidgetCache {
    private static func feedFile(_ feed: FeedKey) -> String { "feed-\(feed.name).json" }

    static func load(feed: FeedKey) -> FeedSnapshot? {
        guard let url = SharedStore.cacheDirectory?.appendingPathComponent(feedFile(feed)),
              let data = try? Data(contentsOf: url)
        else { return nil }
        return try? JSONDecoder().decode(FeedSnapshot.self, from: data)
    }

    static func save(_ snapshot: FeedSnapshot, feed: FeedKey) throws {
        let dir = try ensureDirectory()
        try JSONEncoder().encode(snapshot).write(to: dir.appendingPathComponent(feedFile(feed)), options: .atomic)
    }

    static func remove(feed: FeedKey) {
        guard let url = SharedStore.cacheDirectory?.appendingPathComponent(feedFile(feed)) else { return }
        try? FileManager.default.removeItem(at: url)
    }

    /// 지금까지 위젯이 한 번이라도 그린 피드들 (알림이 오면 이것들을 모두 새로 받는다)
    static func savedFeeds() -> [FeedKey] {
        guard let dir = SharedStore.cacheDirectory,
              let names = try? FileManager.default.contentsOfDirectory(atPath: dir.path)
        else { return [] }
        return names.compactMap { name in
            guard name.hasPrefix("feed-"), name.hasSuffix(".json") else { return nil }
            return FeedKey(name: String(name.dropFirst("feed-".count).dropLast(".json".count)))
        }
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

    /// 어느 피드에도 없는 사진 파일을 지운다. 다른 확장이 방금 쓴 파일은 남겨 두려고 1분 이내 파일은 건드리지 않는다.
    static func removeUnusedImages() {
        guard let dir = SharedStore.cacheDirectory,
              let files = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])
        else { return }
        let used = Set(savedFeeds().flatMap { load(feed: $0)?.items.map(\.imageFile) ?? [] })
        let cutoff = Date().addingTimeInterval(-60)
        for file in files where file.pathExtension == "jpg" && !used.contains(file.lastPathComponent) {
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

/// 위젯에서 지금 몇 번째 사진을 보고 있는지 (피드마다). 새 사진이 오면 맨 앞으로 돌아간다.
enum WidgetPager {
    private static func indexKey(_ feed: FeedKey) -> String { "pager.\(feed.name).index" }
    private static func newestKey(_ feed: FeedKey) -> String { "pager.\(feed.name).newest" }
    private static let flippedAtKey = "pager.flippedAt"

    static func page(feed: FeedKey, in snapshot: FeedSnapshot) -> Int {
        guard let newest = snapshot.items.first, let defaults = SharedStore.defaults,
              defaults.integer(forKey: newestKey(feed)) == newest.momentId
        else { return 0 }
        return min(max(defaults.integer(forKey: indexKey(feed)), 0), snapshot.items.count - 1)
    }

    /// 다음(+1) · 이전(-1). 끝에서 넘기면 처음으로 돌아간다.
    static func flip(feed: FeedKey, by step: Int) {
        guard let snapshot = WidgetCache.load(feed: feed), let newest = snapshot.items.first,
              let defaults = SharedStore.defaults
        else { return }
        let count = snapshot.items.count
        let next = ((page(feed: feed, in: snapshot) + step) % count + count) % count
        defaults.set(next, forKey: indexKey(feed))
        defaults.set(newest.momentId, forKey: newestKey(feed))
        defaults.set(Date().timeIntervalSince1970, forKey: flippedAtKey)
    }

    /// 방금 넘겼으면 위젯은 서버에 가지 않고 바로 다시 그린다 (넘기기가 느려지지 않게)
    static var flippedRecently: Bool {
        guard let at = SharedStore.defaults?.double(forKey: flippedAtKey), at > 0 else { return false }
        return Date().timeIntervalSince1970 - at < 3
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
    /// 피드 하나를 서버에서 받아 캐시를 갱신한다. 바뀐 게 없으면 304 로 빠르게 끝난다.
    static func refresh(feed: FeedKey, timeout: TimeInterval, source: SyncSource) async -> SyncResult {
        guard let token = SharedStore.accessToken,
              let base = SharedStore.apiBaseUrl,
              let userId = SharedStore.userId
        else {
            WidgetCache.clear()
            return .signedOut
        }

        var components = URLComponents(url: base.appendingPathComponent("api/widget/feed"), resolvingAgainstBaseURL: false)
        if case let .friend(id) = feed {
            components?.queryItems = [URLQueryItem(name: "from", value: String(id))]
        }
        guard let url = components?.url else { return .failed }
        let session = makeSession(timeout: timeout)
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(source.rawValue, forHTTPHeaderField: "X-Widget-Source")
        let cached = WidgetCache.load(feed: feed).flatMap { $0.userId == userId ? $0 : nil }
        if let cached, let etag = cached.etag, cached.items.allSatisfy({ WidgetCache.imageExists($0.imageFile) }) {
            request.setValue(etag, forHTTPHeaderField: "If-None-Match")
        }

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { return .failed }
            guard SharedStore.accessToken == token, SharedStore.userId == userId else { return .failed }
            switch http.statusCode {
            case 200: break
            case 304: return .unchanged
            case 204:
                // 보여 줄 사진이 없다 (그 친구가 보낸 게 없거나 친구가 아니게 됐다)
                WidgetCache.remove(feed: feed)
                return .empty
            case 401:
                WidgetCache.clear()
                return .signedOut
            default: return .failed
            }

            let latest = try makeDecoder().decode(WidgetFeedResponse.self, from: data)
            var items: [FeedItem] = []
            for item in latest.items {
                let imageFile = "moment-\(item.moment.id).jpg"
                // 댓글만 바뀐 사진은 다시 받지 않는다. 사진을 못 받은 장은 이번에는 빼고 넘긴다.
                if !WidgetCache.imageExists(imageFile) {
                    guard let url = URL(string: item.moment.thumbUrl, relativeTo: base)?.absoluteURL,
                          let (imageData, imageResponse) = try? await session.data(from: url),
                          (imageResponse as? HTTPURLResponse)?.statusCode == 200,
                          let jpeg = ImageDownsampler.jpeg(from: imageData, maxPixel: 600),
                          (try? WidgetCache.writeImage(jpeg, name: imageFile)) != nil
                    else { continue }
                }
                items.append(FeedItem(
                    momentId: item.moment.id,
                    senderId: item.moment.sender.id,
                    senderName: item.moment.sender.displayName,
                    createdAt: item.moment.createdAt,
                    comments: item.comments,
                    commentCount: item.commentCount,
                    imageFile: imageFile
                ))
            }
            // 가장 새 사진을 못 받았으면 다음에 다시 (ETag 를 저장하지 않아 304 로 막히지 않는다)
            guard items.first?.momentId == latest.items.first?.moment.id else { return .failed }
            let complete = items.count == latest.items.count
            guard SharedStore.accessToken == token, SharedStore.userId == userId else { return .failed }
            try WidgetCache.save(FeedSnapshot(
                etag: complete ? (http.value(forHTTPHeaderField: "ETag") ?? "\"\(latest.version)\"") : nil,
                userId: userId,
                items: items
            ), feed: feed)
            WidgetCache.removeUnusedImages()
            return .updated
        } catch {
            return .failed
        }
    }

    /// 알림 서비스 확장: 모든 친구 피드와, 위젯이 그리고 있는 한 친구 피드들을 한꺼번에 새로 받는다.
    static func refreshAll(timeout: TimeInterval, source: SyncSource) async -> Bool {
        let feeds = Set([FeedKey.everyone] + WidgetCache.savedFeeds())
        return await withTaskGroup(of: SyncResult.self) { group in
            for feed in feeds {
                group.addTask { await refresh(feed: feed, timeout: timeout, source: source) }
            }
            var changed = false
            for await result in group where result == .updated || result == .empty {
                changed = true
            }
            return changed
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
