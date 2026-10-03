import { Redirect } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { useEffect } from 'react';
import { Platform, Text, View } from 'react-native';

/** 웹 미리보기의 로그인 돌아오는 주소. 앱(junseo://auth-callback)으로 열리면 할 일이 없으니 처음 화면으로 보낸다. */
export default function AuthCallback() {
  useEffect(() => {
    if (Platform.OS === 'web') WebBrowser.maybeCompleteAuthSession();
  }, []);
  if (Platform.OS !== 'web') return <Redirect href="/" />;
  return <View><Text>로그인을 마무리하고 있어요.</Text></View>;
}
