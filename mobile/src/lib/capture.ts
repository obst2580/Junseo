import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

const SIZE = 1080;

/**
 * 찍은 사진을 가운데 기준 정사각형으로 자르고 1080px JPEG로 줄인다.
 * 다시 인코딩하면서 위치정보 같은 EXIF 가 빠지고, 서버도 한 번 더 지운다.
 */
export async function toSquareJpeg(uri: string, width: number, height: number): Promise<string> {
  const side = Math.min(width, height);
  const context = ImageManipulator.manipulate(uri)
    .crop({ originX: (width - side) / 2, originY: (height - side) / 2, width: side, height: side })
    .resize({ width: Math.min(SIZE, side), height: Math.min(SIZE, side) });
  const image = await context.renderAsync();
  const result = await image.saveAsync({ format: SaveFormat.JPEG, compress: 0.85 });
  return result.uri;
}
