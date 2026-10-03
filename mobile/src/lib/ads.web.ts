/**
 * 웹 미리보기에는 AdMob 이 없어서 가짜 광고를 띄운다. 5초를 다 보면 true, 그 전에 닫으면 false.
 * 앱(iOS)은 ads.ts 의 보상형 광고를 쓴다.
 */
const SECONDS = 5;

export function showRewardedAd(): Promise<boolean> {
  return new Promise((resolve) => {
    const wrap = document.createElement('div');
    wrap.setAttribute('role', 'dialog');
    wrap.setAttribute('aria-label', '광고');
    Object.assign(wrap.style, {
      position: 'fixed',
      inset: '0',
      zIndex: '9999',
      background: '#000',
      color: '#fff',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '14px',
      fontFamily: '-apple-system, system-ui, sans-serif',
    });
    wrap.innerHTML = `
      <div style="font-size:13px;opacity:.6">광고 · 웹 미리보기</div>
      <div style="width:260px;height:260px;border-radius:24px;background:linear-gradient(135deg,#29ff01,#16323a);display:grid;place-items:center;font-size:22px;font-weight:800;color:#0e0d0c">여기에 광고가 나와요</div>
      <div data-count style="font-size:15px;font-weight:700"></div>
      <button data-close aria-label="광고 닫기" style="position:absolute;top:18px;right:18px;width:36px;height:36px;border-radius:18px;border:0;background:rgba(255,255,255,.15);color:#fff;font-size:18px;cursor:pointer">✕</button>`;
    document.body.appendChild(wrap);
    const count = wrap.querySelector<HTMLElement>('[data-count]')!;
    let left = SECONDS;
    const tick = () => {
      count.textContent = left > 0 ? `${left}초 뒤에 보상을 받아요` : '보상을 받았어요 · 닫아 주세요';
    };
    tick();
    const timer = setInterval(() => {
      left -= 1;
      tick();
      if (left <= 0) clearInterval(timer);
    }, 1000);
    wrap.querySelector('[data-close]')!.addEventListener('click', () => {
      clearInterval(timer);
      wrap.remove();
      resolve(left <= 0);
    });
  });
}
