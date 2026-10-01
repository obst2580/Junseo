import { StyleSheet, View } from 'react-native';

import { Avatar } from '@/components/ui';
import type { UserSummary } from '@/lib/api';
import { colors } from '@/lib/theme';

/** 단체방 얼굴: 두 사람 아바타를 비스듬히 겹친다. */
export function GroupAvatar({ members, size = 48 }: { members: UserSummary[]; size?: number }) {
  const [a, b] = members;
  const small = Math.round(size * 0.66);
  if (!a) return <View style={{ width: size, height: size }} />;
  return (
    <View style={{ width: size, height: size }}>
      <View style={styles.back}>
        <Avatar id={a.id} name={a.displayName} size={small} />
      </View>
      {b && (
        <View style={[styles.front, { borderRadius: small / 2 + 2 }]}>
          <Avatar id={b.id} name={b.displayName} size={small} />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  back: { position: 'absolute', top: 0, left: 0 },
  front: { position: 'absolute', right: -2, bottom: -2, borderWidth: 2, borderColor: colors.bg },
});
