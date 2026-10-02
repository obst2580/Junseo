import SwiftUI
import UIKit
import WidgetKit

// 친구가 보낸 최신 사진을 홈 화면에 띄우고, 사진 아래에 댓글을 보여준다.
// 저장한 디자인: 큰 위젯 댓글 2줄, 이모지 숨김, 보낸 사람은 글자만.
// 배치는 앱 안의 미리보기(src/components/WidgetPreview.tsx)와 맞춰 둔다.

private enum Palette {
    static let accent = Color(red: 0.161, green: 1, blue: 0.004) // #29FF01 (로고 초록)
    static let background = Color(red: 0.055, green: 0.051, blue: 0.047) // #0e0d0c
}

struct MomentEntry: TimelineEntry {
    enum State {
        case signedOut
        case empty
        case content(WidgetSnapshot, imageURL: URL?)
        case placeholder
    }

    let date: Date
    let state: State

    static func current() -> MomentEntry {
        guard SharedStore.accessToken != nil, let userId = SharedStore.userId else {
            return MomentEntry(date: .now, state: .signedOut)
        }
        guard let snapshot = WidgetCache.load(), snapshot.userId == userId else {
            return MomentEntry(date: .now, state: .empty)
        }
        return MomentEntry(date: .now, state: .content(snapshot, imageURL: WidgetCache.imageURL(snapshot.imageFile)))
    }
}

struct MomentProvider: TimelineProvider {
    func placeholder(in context: Context) -> MomentEntry {
        MomentEntry(date: .now, state: .placeholder)
    }

    func getSnapshot(in context: Context, completion: @escaping (MomentEntry) -> Void) {
        let entry = MomentEntry.current()
        if context.isPreview, case .content = entry.state {
            completion(entry)
        } else if context.isPreview {
            completion(placeholder(in: context))
        } else {
            completion(entry)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MomentEntry>) -> Void) {
        Task {
            // 알림 서비스 확장이 먼저 캐시를 채워 뒀다면 304 로 바로 끝난다.
            _ = await WidgetSync.refresh(timeout: 8)
            // 푸시가 오지 않아도 15분 뒤에는 다시 확인하도록 예약한다 (실제 시점은 iOS가 예산에 맞춰 정한다).
            let next = Date().addingTimeInterval(15 * 60)
            completion(Timeline(entries: [MomentEntry.current()], policy: .after(next)))
        }
    }
}

struct MomentWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: MomentEntry

    var body: some View {
        switch entry.state {
        case .signedOut:
            MessageView(symbol: "person.crop.circle", text: "앱에서 로그인해 주세요")
        case .empty:
            MessageView(symbol: "camera.fill", text: "친구가 사진을 보내면\n여기에 떠요")
        case .placeholder:
            MomentContentView(snapshot: nil, imageURL: nil, now: entry.date, large: family == .systemLarge)
        case let .content(snapshot, imageURL):
            MomentContentView(snapshot: snapshot, imageURL: imageURL, now: entry.date, large: family == .systemLarge)
                .widgetURL(URL(string: "junseo://moments/\(snapshot.momentId)"))
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
    let snapshot: WidgetSnapshot?
    let imageURL: URL?
    let now: Date
    let large: Bool

    private var hasActivity: Bool {
        guard let snapshot else { return false }
        return !snapshot.comments.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: large ? 6 : 4) {
            if let snapshot {
                SenderLabel(name: snapshot.senderName, ago: Self.ago(snapshot.createdAt, now: now))
            }
            Spacer(minLength: 0)
            if let snapshot {
                ForEach(Array(snapshot.comments.suffix(large ? 2 : 1).enumerated()), id: \.offset) { _, comment in
                    Text("\(Text(comment.author).fontWeight(.heavy)) \(comment.text)")
                        .font(large ? .subheadline : .caption2)
                        .foregroundStyle(.white)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
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
                        .init(color: .black.opacity(hasActivity ? 0.75 : 0), location: 1),
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
        StaticConfiguration(kind: SharedConfig.widgetKind, provider: MomentProvider()) { entry in
            MomentWidgetView(entry: entry)
        }
        .configurationDisplayName("친구 사진")
        .description("친구가 보낸 최신 사진과 댓글을 보여줘요.")
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
