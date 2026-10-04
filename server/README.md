# server 폴더

내 PC에서 서버를 돌릴 때 쓰는 폴더예요. 실행에 필요한 스크립트만 저장소에 올라가고,
월드·로그·paper.jar 같은 파일은 git 에 올라가지 않아요 (`.gitignore`).

```
server/
├── start.bat        ← Windows: 더블클릭
├── start.sh         ← macOS/Linux: ./start.sh
├── paper.jar        ← papermc.io 에서 받아서 넣기
└── plugins/
    └── JunseoCity-0.1.0.jar   ← 이 플러그인
```

자세한 방법은 저장소 맨 위의 README.md 를 보세요.
