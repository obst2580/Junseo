import { requireNativeModule } from 'expo-modules-core';
import { Platform } from 'react-native';

type AndroidWidget = {
  getTokenAsync(): Promise<string | null>;
  setTokenAsync(token: string | null): Promise<void>;
  signIn(apiUrl: string, userId: number): void;
  signOut(): void;
  setFriends(json: string): void;
  reload(): void;
  requestPin(): boolean;
  rememberPushToken(token: string): void;
  pushToken(): string | null;
};

export const androidWidget = Platform.OS === 'android'
  ? requireNativeModule<AndroidWidget>('JunseoWidget') : null;
