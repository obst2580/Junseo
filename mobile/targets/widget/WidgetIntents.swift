import AppIntents
import WidgetKit

/// 위젯 편집(홈 화면에서 위젯을 길게 누르기)에서 고르는 친구. 비워 두면 모든 친구.
struct FriendEntity: AppEntity {
    static var typeDisplayRepresentation: TypeDisplayRepresentation { "친구" }
    static var defaultQuery: FriendQuery { FriendQuery() }

    let id: Int
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }
}

/// 친구 목록은 앱이 App Group 에 넣어 둔 것을 쓴다 (src/lib/widgetBridge.ts 의 setFriends).
struct FriendQuery: EntityQuery {
    func entities(for identifiers: [FriendEntity.ID]) async throws -> [FriendEntity] {
        let friends = Self.all()
        // 친구가 아니게 됐어도 고른 것은 남겨 둔다 (그 위젯은 「사진이 없어요」로 보인다)
        return identifiers.map { id in friends.first { $0.id == id } ?? FriendEntity(id: id, name: "친구") }
    }

    func suggestedEntities() async throws -> [FriendEntity] {
        Self.all()
    }

    private static func all() -> [FriendEntity] {
        SharedStore.friends.map { FriendEntity(id: $0.id, name: $0.displayName) }
    }
}

/// 위젯 설정: 누구 사진을 보여 줄지
struct MomentWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource { "친구 사진" }
    static var description: IntentDescription { IntentDescription("모든 친구, 또는 한 친구가 보낸 사진만 보여줘요.") }

    @Parameter(title: "친구")
    var friend: FriendEntity?

    var feed: FeedKey { friend.map { .friend($0.id) } ?? .everyone }
}

/// 위젯 양옆을 누르면 이전 · 다음 사진. 앱을 열지 않고 위젯 안에서 바로 넘어간다 (iOS 17+).
struct FlipPhotoIntent: AppIntent {
    static var title: LocalizedStringResource { "사진 넘기기" }
    static var isDiscoverable: Bool { false }

    @Parameter(title: "피드")
    var feed: String

    @Parameter(title: "방향")
    var step: Int

    init() {}

    init(feed: FeedKey, step: Int) {
        self.feed = feed.name
        self.step = step
    }

    func perform() async throws -> some IntentResult {
        if let key = FeedKey(name: feed) {
            WidgetPager.flip(feed: key, by: step)
        }
        return .result()
    }
}
