import ExpoModulesCore
import WidgetKit

/// Lets the app see whether a junseo widget is on the home screen, so right after login it can ask the person to
/// place one (and notice when they have). iOS has no API for an app to add a widget itself.
public class JunseoWidgetStatusModule: Module {
  public func definition() -> ModuleDefinition {
    Name("JunseoWidgetStatus")

    /// Number of junseo widgets on the home screen (and lock screen), or -1 when iOS cannot tell.
    AsyncFunction("installedCountAsync") { (promise: Promise) in
      WidgetCenter.shared.getCurrentConfigurations { result in
        switch result {
        case .success(let widgets):
          promise.resolve(widgets.filter { $0.kind == "MomentWidget" }.count)
        case .failure:
          promise.resolve(-1)
        }
      }
    }
  }
}
