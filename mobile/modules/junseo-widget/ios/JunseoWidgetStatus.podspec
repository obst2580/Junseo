Pod::Spec.new do |s|
  s.name           = 'JunseoWidgetStatus'
  s.version        = '1.0.0'
  s.summary        = 'Tells the app whether a Junseo widget is on the home screen'
  s.description    = 'WidgetCenter lookup used by the post-login widget setup screen'
  s.license        = 'UNLICENSED'
  s.author         = 'Junseo'
  s.homepage       = 'https://github.com/obst2580/Junseo'
  s.platforms      = { :ios => '17.0' }
  s.swift_version  = '5.9'
  s.source         = { git: 'https://github.com/obst2580/Junseo.git' }
  s.static_framework = true
  s.frameworks     = 'WidgetKit'

  s.dependency 'ExpoModulesCore'

  s.source_files = "**/*.swift"
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }
end
