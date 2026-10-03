import * as WebBrowser from 'expo-web-browser';
import { useEffect } from 'react';
import { Platform, Text, View } from 'react-native';

export default function AuthCallback() {
  useEffect(() => {
    if (Platform.OS === 'web') WebBrowser.maybeCompleteAuthSession();
  }, []);
  return <View><Text>로그인을 마무리하고 있어요.</Text></View>;
}
