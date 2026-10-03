import { Platform } from 'react-native';

/**
 * 렌즈 굴곡 효과(어안 렌즈처럼 가운데가 볼록하게 커지고 가장자리가 휘어 들어감).
 * 사진 자체에 그려 넣어서, 보이는 그대로 보내지고 위젯에도 그대로 뜬다. 디자인 랩도 같은 식을 쓴다.
 *
 * 가운데에서 거리 r 인 점은 원본에서 R·(r/R)^gamma 거리를 가져온다 (R: 가운데~모서리).
 * gamma > 1 이면 가운데는 확대되고 바깥은 눌린다. 모서리는 모서리 그대로라 빈 곳이 생기지 않는다.
 */
export const LENS = { gamma: 1.6 };

const SKSL = `
uniform shader image;
uniform float2 size;
uniform float gamma;

half4 main(float2 p) {
  float2 c = size * 0.5;
  float2 d = p - c;
  float r = length(d);
  if (r < 0.5) {
    return image.eval(c);
  }
  float R = length(c);
  float rs = R * pow(r / R, gamma);
  return image.eval(c + d * (rs / r));
}`;

type SkiaModule = typeof import('@shopify/react-native-skia');
let skia: Promise<SkiaModule> | null = null;

// 웹은 CanvasKit(wasm)을 먼저 불러와야 Skia 를 쓸 수 있다 (npm run web 이 public/ 에 복사해 둔다)
function loadSkia(): Promise<SkiaModule> {
  skia ??= (async () => {
    if (Platform.OS === 'web') {
      const { LoadSkiaWeb } = await import('@shopify/react-native-skia/lib/module/web');
      await LoadSkiaWeb({ locateFile: (file: string) => `/${file}` });
    }
    return import('@shopify/react-native-skia');
  })();
  return skia;
}

/** 웹은 CanvasKit 을 처음 받는 데 몇 초 걸려서, 사진을 찍자마자 미리 불러 둔다. */
export function preloadLens() {
  loadSkia().catch(() => {
    skia = null;
  });
}

/** 사진(uri)에 렌즈 효과를 넣은 새 JPEG 의 uri. 웹은 data URI, 앱은 캐시 폴더의 파일. */
export async function applyLens(uri: string, gamma = LENS.gamma): Promise<string> {
  const { Skia, TileMode, FilterMode, MipmapMode, ImageFormat } = await loadSkia();
  const image = Skia.Image.MakeImageFromEncoded(await Skia.Data.fromURI(uri));
  if (!image) throw new Error('사진을 읽지 못했어요.');
  const width = image.width();
  const height = image.height();
  const effect = Skia.RuntimeEffect.Make(SKSL);
  const surface = Skia.Surface.Make(width, height);
  if (!effect || !surface) throw new Error('렌즈 효과를 만들지 못했어요.');

  const paint = Skia.Paint();
  paint.setShader(
    effect.makeShaderWithChildren([width, height, gamma], [image.makeShaderOptions(TileMode.Clamp, TileMode.Clamp, FilterMode.Linear, MipmapMode.None)]),
  );
  surface.getCanvas().drawRect(Skia.XYWHRect(0, 0, width, height), paint);
  surface.flush();
  const result = surface.makeImageSnapshot();

  if (Platform.OS === 'web') return `data:image/jpeg;base64,${result.encodeToBase64(ImageFormat.JPEG, 92)}`;
  // 업로드(FormData)는 파일 경로가 필요해서 캐시 폴더에 쓴다
  const { File, Paths } = await import('expo-file-system');
  const file = new File(Paths.cache, `lens-${Date.now()}.jpg`);
  file.write(result.encodeToBytes(ImageFormat.JPEG, 92));
  return file.uri;
}
