import type { ReactNode } from 'react';
import { ActivityIndicator, StyleSheet, Text, TextInput, View, type TextInputProps, type ViewStyle } from 'react-native';

import { PressScale } from '@/components/PressScale';
import { Icon, type IconName } from '@/components/Icon';
import { avatarColor, colors, icon as iconToken, radius } from '@/lib/theme';

export function Avatar({ id, name, size = 36 }: { id: number; name: string; size?: number }) {
  return (
    <View style={[styles.avatar, { width: size, height: size, borderRadius: size / 2, backgroundColor: avatarColor(id) }]}>
      <Text style={[styles.avatarText, { fontSize: size * 0.42 }]}>{name.slice(0, 1)}</Text>
    </View>
  );
}


export function IconButton({
  icon,
  onPress,
  size = iconToken.size,
  badge,
  label,
  style,
}: {
  icon: IconName;
  onPress: () => void;
  size?: number;
  badge?: number;
  label?: string;
  style?: ViewStyle;
}) {
  return (
    <PressScale onPress={onPress} accessibilityLabel={label} hitSlop={8} style={[styles.iconButton, style]}>
      <Icon name={icon} size={size} color={colors.text} />
      {!!badge && (
        <View style={styles.badge}>
          <Text style={styles.badgeText}>{badge > 99 ? '99+' : badge}</Text>
        </View>
      )}
    </PressScale>
  );
}

export function Button({
  title,
  onPress,
  loading,
  disabled,
  variant = 'primary',
  style,
}: {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
  style?: ViewStyle;
}) {
  const bg = variant === 'primary' ? colors.accent : colors.surfaceHigh;
  const fg = variant === 'primary' ? colors.accentText : variant === 'danger' ? colors.danger : colors.text;
  return (
    <PressScale
      onPress={onPress}
      disabled={disabled || loading}
      style={[styles.button, { backgroundColor: bg }, (disabled || loading) && styles.disabled, style]}>
      {loading ? <ActivityIndicator color={fg} /> : <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>}
    </PressScale>
  );
}

export function Field(props: TextInputProps & { label?: string }) {
  const { label, style, ...rest } = props;
  return (
    <View style={styles.fieldWrap}>
      {label && <Text style={styles.fieldLabel}>{label}</Text>}
      <TextInput placeholderTextColor={colors.textFaint} style={[styles.field, style]} {...rest} />
    </View>
  );
}

export function Empty({ icon, title, children }: { icon: IconName; title: string; children?: ReactNode }) {
  return (
    <View style={styles.empty}>
      <Icon name={icon} size={40} color={colors.textFaint} strokeWidth={2} />
      <Text style={styles.emptyTitle}>{title}</Text>
      {children}
    </View>
  );
}

export function ErrorText({ children }: { children: ReactNode }) {
  return <Text style={styles.error}>{children}</Text>;
}

const styles = StyleSheet.create({
  avatar: { alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: '#1a1a1a', fontWeight: '800' },
  iconButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.surfaceHigh,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badge: {
    position: 'absolute',
    top: -2,
    right: -2,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeText: { color: colors.accentText, fontSize: 11, fontWeight: '800' },
  button: { height: 52, borderRadius: radius.button, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 20 },
  buttonText: { fontSize: 16, fontWeight: '700' },
  disabled: { opacity: 0.45 },
  fieldWrap: { gap: 6 },
  fieldLabel: { color: colors.textDim, fontSize: 13, fontWeight: '600', marginLeft: 4 },
  field: {
    height: 52,
    borderRadius: 16,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    color: colors.text,
    fontSize: 16,
    paddingHorizontal: 16,
  },
  empty: { alignItems: 'center', justifyContent: 'center', gap: 12, padding: 32 },
  emptyTitle: { color: colors.textDim, fontSize: 15, textAlign: 'center', lineHeight: 22 },
  error: { color: colors.danger, fontSize: 14, textAlign: 'center' },
});
