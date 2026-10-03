import { useEffect, useMemo, useRef } from 'react';
import { Alert, Platform } from 'react-native';

import { useActionSheet, type SheetOption } from '@/components/ActionSheet';
import { api, ApiError, type ReportKind, type ReportReason, type UserSummary } from '@/lib/api';
import { events } from '@/lib/events';
import { widgetBridge } from '@/lib/widgetBridge';

/** 무엇을 신고하는지. what: 메뉴에 쓰는 이름 (사진 · 댓글 · 메시지). 사람 자체면 kind 'user'. */
export type ReportTarget = { kind: ReportKind; targetId?: number; user: UserSummary; what?: string };

const REASONS: [ReportReason, string][] = [
  ['spam', '스팸 · 광고'],
  ['abuse', '괴롭힘 · 욕설'],
  ['sexual', '성적인 내용'],
  ['violence', '폭력 · 위험한 내용'],
  ['other', '기타'],
];

function say(title: string, detail?: string) {
  if (Platform.OS === 'web') globalThis.alert?.(detail ? `${title}\n${detail}` : title);
  else Alert.alert(title, detail);
}

function ask(title: string, detail: string, action: string, run: () => void) {
  if (Platform.OS === 'web') {
    if (globalThis.confirm?.(`${title}\n${detail}`)) run();
    return;
  }
  Alert.alert(title, detail, [
    { text: '취소', style: 'cancel' },
    { text: action, style: 'destructive', onPress: run },
  ]);
}

/**
 * 신고 · 차단 메뉴. menu() 로 「…신고하기 / …님 차단하기」 시트를 띄우고, element 를 화면에 그려 둔다.
 * 신고 → 이유 고르기 → 「신고했어요」 + 바로 차단하기. 차단 → 확인 → 친구 끊김, 목록 새로 고침, onBlocked.
 */
export function useSafety(onBlocked?: (user: UserSummary) => void) {
  const sheet = useActionSheet();
  const blockedRef = useRef(onBlocked);
  useEffect(() => {
    blockedRef.current = onBlocked;
  });
  const open = sheet.open;

  // 함수들은 참조가 바뀌지 않는다: 말풍선(memo)에 넘겨도 다시 그려지지 않게
  const actions = useMemo(() => {
    const block = (user: UserSummary) =>
      ask(
        `${user.displayName}님을 차단할까요?`,
        '친구가 끊기고 서로의 사진이 사라져요. 댓글 · 메시지도 더 이상 보이지 않아요. 상대에게는 알리지 않아요.',
        '차단',
        async () => {
          try {
            await api.block(user.id);
            events.emit('friends');
            events.emit('moments');
            events.emit('messages');
            widgetBridge.reload();
            blockedRef.current?.(user);
          } catch (e) {
            say(e instanceof ApiError ? e.message : '차단하지 못했어요.');
          }
        },
      );

    const send = async (t: ReportTarget, reason: ReportReason) => {
      try {
        await api.report({
          kind: t.kind,
          targetId: t.kind === 'user' ? undefined : t.targetId,
          userId: t.kind === 'user' ? t.user.id : undefined,
          reason,
        });
        open({
          title: '신고했어요',
          message: '24시간 안에 확인하고, 약관을 어긴 내용이면 지우고 올린 사람을 내보낼게요.',
          options: [{ label: `${t.user.displayName}님 차단하기`, destructive: true, onPress: () => block(t.user) }],
        });
      } catch (e) {
        say(e instanceof ApiError ? e.message : '신고하지 못했어요.');
      }
    };

    const report = (t: ReportTarget) =>
      open({
        title: '신고하는 이유를 골라 주세요',
        message: '신고는 운영자만 봐요. 상대에게는 알리지 않아요.',
        options: REASONS.map(([reason, label]) => ({ label, onPress: () => void send(t, reason) })),
      });

    /** extra: 신고 · 차단 앞에 붙는 선택지 (예: 댓글 삭제, 친구 삭제) */
    const menu = (t: ReportTarget, extra: SheetOption[] = []) =>
      open({
        options: [
          ...extra,
          { label: t.kind === 'user' ? '신고하기' : `${t.what ?? ''} 신고하기`.trim(), destructive: true, onPress: () => report(t) },
          { label: `${t.user.displayName}님 차단하기`, destructive: true, onPress: () => block(t.user) },
        ],
      });

    return { menu, report, block };
  }, [open]);

  return { element: sheet.element, ...actions };
}
