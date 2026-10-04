// CI 에서 시뮬레이터 스크린숏의 글자를 읽어 출력한다 (macOS Vision). 앱이 실제로 화면을 그렸는지 확인하는 용도.
// 사용: swift ocr.swift <이미지> [꼭 있어야 하는 글자...]  → 없으면 1 로 끝난다
import AppKit
import Foundation
import Vision

let args = CommandLine.arguments
guard args.count >= 2,
      let image = NSImage(contentsOf: URL(fileURLWithPath: args[1])),
      let cgImage = image.cgImage(forProposedRect: nil, context: nil, hints: nil)
else {
    print("이미지를 읽지 못했어요")
    exit(1)
}

let request = VNRecognizeTextRequest()
request.recognitionLevel = .accurate
request.recognitionLanguages = ["ko-KR", "en-US"]
try VNImageRequestHandler(cgImage: cgImage).perform([request])
let lines = (request.results ?? []).compactMap { $0.topCandidates(1).first?.string }
print("화면 글자: \(lines.joined(separator: " | "))")

let missing = args.dropFirst(2).filter { word in !lines.contains { $0.contains(word) } }
if !missing.isEmpty {
    print("화면에 없어요: \(missing.joined(separator: ", "))")
    exit(1)
}
