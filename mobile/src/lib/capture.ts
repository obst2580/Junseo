import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';
import type { RefObject } from 'react';
import { PixelRatio, Platform, type View } from 'react-native';
import { captureRef } from 'react-native-view-shot';

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

/**
 * 사진과 그 위에 얹은 눈 가리개·텍스트를 한 장의 1080px JPEG로 합친다.
 * iOS 는 크기를 pt 로 받아 화면 배율을 곱하고, 웹은 px 로 받는다.
 */
export async function flattenPhoto(view: RefObject<View | null>): Promise<string> {
  const web = Platform.OS === 'web';
  const side = web ? SIZE : SIZE / PixelRatio.get();
  const uri = await captureRef(view, { format: 'jpg', quality: 0.9, width: side, height: side, result: web ? 'data-uri' : 'tmpfile' });
  // iOS 는 file:// 없는 경로를 준다
  return web || uri.includes('://') ? uri : `file://${uri}`;
}

/** 화면에 그린 뷰를 width×height px 그림으로 (템플릿 저장용) */
export async function captureView(view: RefObject<View | null>, width: number, height: number): Promise<string> {
  const web = Platform.OS === 'web';
  const ratio = web ? 1 : PixelRatio.get();
  const uri = await captureRef(view, { format: 'jpg', quality: 0.92, width: width / ratio, height: height / ratio, result: web ? 'data-uri' : 'tmpfile' });
  return web || uri.includes('://') ? uri : `file://${uri}`;
}
