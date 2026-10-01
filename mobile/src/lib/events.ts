// 푸시가 오거나 내가 뭔가를 바꿨을 때 열려 있는 화면들이 다시 불러오게 하는 작은 이벤트 버스.
type Topic = 'moments' | 'messages' | 'friends';
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
