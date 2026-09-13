require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "SpotifyRn"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.license      = package["license"]
  s.homepage     = "https://github.com/vandervdb/Spotify-sdk"
  s.authors      = { "Arnaud Vanderbecq" => "https://github.com/vandervdb" }
  s.platforms    = { :ios => "15.1" }
  s.source       = { :git => "https://github.com/vandervdb/Spotify-sdk.git", :tag => "v#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"

  # Le cœur multiplateforme, compilé par Kotlin/Native.
  # `./gradlew :spotify-core:assembleSpotifyCoreXCFramework` le produit dans
  # spotify-core/build/XCFrameworks/release — vérifié : tranches ios-arm64 et
  # ios-arm64_x86_64-simulator, avec en-têtes Objective-C.
  s.vendored_frameworks = "ios/SpotifyCore.xcframework"

  install_modules_dependencies(s)
end
