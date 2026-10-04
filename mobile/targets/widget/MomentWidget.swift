import AppIntents
import SwiftUI
import UIKit
import WidgetKit

// 친구가 보낸 사진을 홈 화면에 띄우고, 사진 아래에 댓글을 보여준다. 양옆을 누르면 최근 사진(최대 5장)을 넘겨 본다.
// 위젯 편집에서 한 친구만 고르면 그 친구 사진만 나온다.
// 저장한 디자인: 큰 위젯 댓글 2줄, 이모지 숨김, 보낸 사람은 글자만.
// 배치는 앱 안의 미리보기(src/components/WidgetPreview.tsx)와 맞춰 둔다.

private enum Palette {
    static let accent = Color(red: 0.161, green: 1, blue: 0.004) // #29FF01 (로고 초록)
    static let background = Color(red: 0.055, green: 0.051, blue: 0.047) // #0e0d0c
}

struct MomentEntry: TimelineEntry {
    enum State {
        case signedOut
        /// 보여 줄 사진이 없다. friendName: 한 친구만 고른 위젯이면 그 친구 이름
        case empty(friendName: String?)
        case content(FeedItem, page: Int, pages: Int, imageURL: URL?)
        case placeholder
    }

    let date: Date
    let feed: FeedKey
    let state: State

    static func current(feed: FeedKey, friendName: String?) -> MomentEntry {
        guard SharedStore.accessToken != nil, let userId = SharedStore.userId else {
            return MomentEntry(date: .now, feed: feed, state: .signedOut)
        }
        guard let snapshot = WidgetCache.load(feed: feed), snapshot.userId == userId, !snapshot.items.isEmpty else {
            return MomentEntry(date: .now, feed: feed, state: .empty(friendName: friendName))
        }
        let page = WidgetPager.page(feed: feed, in: snapshot)
        let item = snapshot.items[page]
        return MomentEntry(
            date: .now,
            feed: feed,
            state: .content(item, page: page, pages: snapshot.items.count, imageURL: WidgetCache.imageURL(item.imageFile))
        )
    }
}

struct MomentProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> MomentEntry {
        MomentEntry(date: .now, feed: .everyone, state: .placeholder)
    }

    func snapshot(for configuration: MomentWidgetIntent, in context: Context) async -> MomentEntry {
        let entry = MomentEntry.current(feed: configuration.feed, friendName: configuration.friend?.name)
        if context.isPreview, case .content = entry.state { return entry }
        return context.isPreview ? placeholder(in: context) : entry
    }

    func timeline(for configuration: MomentWidgetIntent, in context: Context) async -> Timeline<MomentEntry> {
        let feed = configuration.feed
        // 방금 넘겼으면 서버에 가지 않고 바로 그린다. 그 밖에는 알림 서비스 확장이 먼저 채워 뒀다면 304 로 바로 끝난다.
        if !WidgetPager.flippedRecently {
            _ = await WidgetSync.refresh(feed: feed, timeout: 8, source: .widget)
        }
        // 푸시가 오지 않아도 15분 뒤에는 다시 확인하도록 예약한다 (실제 시점은 iOS가 예산에 맞춰 정한다).
        let next = Date().addingTimeInterval(15 * 60)
        return Timeline(entries: [MomentEntry.current(feed: feed, friendName: configuration.friend?.name)], policy: .after(next))
    }
}

struct MomentWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: MomentEntry

    var body: some View {
        switch entry.state {
        case .signedOut:
            MessageView(symbol: "person.crop.circle", text: "앱에서 로그인해 주세요")
        case let .empty(friendName):
            MessageView(
                symbol: "camera.fill",
                text: friendName.map { "\($0)님이 사진을 보내면\n여기에 떠요" } ?? "친구가 사진을 보내면\n여기에 떠요"
            )
        case .placeholder:
            MomentContentView(item: nil, page: 0, pages: 1, feed: entry.feed, imageURL: nil, now: entry.date, large: family == .systemLarge)
        case let .content(item, page, pages, imageURL):
            MomentContentView(item: item, page: page, pages: pages, feed: entry.feed, imageURL: imageURL, now: entry.date, large: family == .systemLarge)
                .widgetURL(URL(string: "junseo://moments/\(item.momentId)"))
        }
    }
}

private struct MessageView: View {
    let symbol: String
    let text: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.title2)
                .foregroundStyle(Palette.accent)
            Text(text)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
        }
        .containerBackground(for: .widget) { Palette.background }
    }
}

private struct MomentContentView: View {
    let item: FeedItem?
    let page: Int
    let pages: Int
    let feed: FeedKey
    let imageURL: URL?
    let now: Date
    let large: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: large ? 6 : 4) {
            HStack(alignment: .center, spacing: 6) {
                if let item {
                    SenderLabel(name: item.senderName, ago: Self.ago(item.createdAt, now: now))
                }
                Spacer(minLength: 0)
                if pages > 1 {
                    PageDots(page: page, pages: pages, large: large)
                }
            }
            Spacer(minLength: 0)
            if let item {
                ForEach(Array(item.comments.suffix(large ? 2 : 1).enumerated()), id: \.offset) { _, comment in
                    Text("\(Text(comment.author).fontWeight(.heavy)) \(comment.text)")
                        .font(large ? .subheadline : .caption2)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        // 양옆을 누르면 넘어간다 (가운데를 누르면 그 사진이 앱에서 열린다)
        .overlay {
            if pages > 1 {
                HStack(spacing: 0) {
                    FlipArea(feed: feed, step: -1, large: large)
                    Spacer(minLength: 0)
                    FlipArea(feed: feed, step: 1, large: large)
                }
            }
        }
        .containerBackground(for: .widget) {
            ZStack {
                Palette.background
                if let imageURL, let image = UIImage(contentsOfFile: imageURL.path) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                }
                LinearGradient(
                    stops: [
                        .init(color: .black.opacity(0.35), location: 0),
                        .init(color: .clear, location: 0.3),
                        .init(color: .clear, location: 0.5),
                        .init(color: .black.opacity(item?.comments.isEmpty == false ? 0.75 : 0), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
        }
    }

    /// "방금", "5분 전" 처럼 짧게. 타임라인이 15분마다 다시 그려져서 대략 맞는다.
    static func ago(_ date: Date, now: Date) -> String {
        if now.timeIntervalSince(date) < 60 { return "방금" }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: date, relativeTo: now)
    }
}

/// 위젯 왼쪽 · 오른쪽 가장자리의 누르는 자리. 살짝 보이는 화살표로 넘길 수 있다는 걸 알린다.
private struct FlipArea: View {
    let feed: FeedKey
    let step: Int
    let large: Bool

    var body: some View {
        Button(intent: FlipPhotoIntent(feed: feed, step: step)) {
            ZStack {
                Color.clear
                Image(systemName: step < 0 ? "chevron.left" : "chevron.right")
                    .font(.system(size: large ? 15 : 11, weight: .bold))
                    .foregroundStyle(.white.opacity(0.75))
                    .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
            }
            .frame(width: large ? 56 : 34)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(step < 0 ? "이전 사진" : "다음 사진")
    }
}

/// 몇 번째 사진인지 (● ○ ○)
private struct PageDots: View {
    let page: Int
    let pages: Int
    let large: Bool

    var body: some View {
        HStack(spacing: large ? 4 : 3) {
            ForEach(0..<pages, id: \.self) { i in
                Circle()
                    .fill(.white.opacity(i == page ? 1 : 0.45))
                    .frame(width: large ? 6 : 4, height: large ? 6 : 4)
            }
        }
        .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(pages)장 중 \(page + 1)번째")
    }
}

/// 보낸 사람은 이름표 없이 글자만. 위쪽 그늘과 글자 그림자로 사진 위에서도 읽힌다.
private struct SenderLabel: View {
    let name: String
    let ago: String

    var body: some View {
        HStack(spacing: 4) {
            Text(name).fontWeight(.bold).lineLimit(1)
            Text(ago)
                .foregroundStyle(.white.opacity(0.72))
                .lineLimit(1)
                .fixedSize()
        }
        .font(.caption2)
        .foregroundStyle(.white)
        .shadow(color: .black.opacity(0.5), radius: 2, y: 1)
    }
}

struct MomentWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: SharedConfig.widgetKind, intent: MomentWidgetIntent.self, provider: MomentProvider()) { entry in
            MomentWidgetView(entry: entry)
        }
        .configurationDisplayName("친구 사진")
        .description("친구가 보낸 사진과 댓글을 보여줘요. 양옆을 누르면 넘어가요.")
        .supportedFamilies([.systemSmall, .systemLarge])
        .momentPushHandlerIfAvailable()
    }
}

extension WidgetConfiguration {
    /// iOS 26 부터는 서버가 위젯 전용 푸시로 직접 갱신을 요청할 수 있다. 그 이하에서는 그대로 둔다.
    /// (`if #available` 로 서로 다른 타입을 돌려주는 건 Swift 5.7 의 SE-0360 덕분에 가능하다.)
    @MainActor
    func momentPushHandlerIfAvailable() -> some WidgetConfiguration {
        if #available(iOS 26.0, *) {
            return pushHandler(MomentWidgetPushHandler.self)
        } else {
            return self
        }
    }
}

@main
struct JunseoWidgetBundle: WidgetBundle {
    var body: some Widget {
        MomentWidget()
    }
}
