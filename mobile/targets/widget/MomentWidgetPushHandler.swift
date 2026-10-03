import WidgetKit

/// iOS 26 위젯 푸시 토큰을 받아 서버에 등록한다.
/// 서버는 새 사진·댓글·반응이 생기면 이 토큰으로 `apns-push-type: widgets` 푸시를 보내고, WidgetKit 이 타임라인을 다시 불러온다.
@available(iOS 26.0, *)
struct MomentWidgetPushHandler: WidgetPushHandler {
    func pushTokenDidChange(_ pushInfo: WidgetPushInfo, widgets: [WidgetInfo]) {
        let token = pushInfo.token.map { String(format: "%02x", $0) }.joined()
        SharedStore.widgetPushToken = token
        Task {
            if widgets.isEmpty {
                // 홈 화면에서 위젯을 모두 지웠다.
                await WidgetAPI.unregister(token: token)
            } else if await WidgetAPI.registerWidgetToken(token) {
                SharedStore.markWidgetPushTokenRegistered(token)
            }
            // 실패하면 앱이 다음에 열릴 때 widgetPushToken 을 보고 대신 등록한다 (src/lib/push.ts).
        }
    }
}
