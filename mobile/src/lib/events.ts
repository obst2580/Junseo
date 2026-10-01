// 푸시가 오거나 내가 뭔가를 바꿨을 때 열려 있는 화면들이 다시 불러오게 하는 작은 이벤트 버스.
// messages: 대화가 바뀌었다 (열린 채팅 화면·대화 목록·탭 배지가 다시 불러온다)
// unread: 안 읽은 수·마지막 메시지만 바뀌었다 (대화 목록·탭 배지만. 채팅 화면은 실시간 신호로 따로 받는다)
type Topic = 'moments' | 'messages' | 'unread' | 'friends';
type Listener = () => void;

const listeners = new Map<Topic, Set<Listener>>();

export const events = {
  on(topic: Topic, listener: Listener) {
    const set = listeners.get(topic) ?? new Set();
    set.add(listener);
    listeners.set(topic, set);
    return () => void set.delete(listener);
  },
  emit(topic: Topic) {
    listeners.get(topic)?.forEach((l) => l());
  },
};
