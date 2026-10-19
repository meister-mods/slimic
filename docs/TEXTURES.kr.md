# 텍스처 편집

[English](TEXTURES.md) · [개발 가이드](DEVELOPMENT.kr.md#시각-에셋-규칙)

동료 장식은 [`assets/slimic/textures/entity/accessories`](../src/main/resources/assets/slimic/textures/entity/accessories)의 전용 PNG 아틀라스를 사용합니다. 리소스 팩의 같은 `assets/slimic/` 경로에 파일을 배치하면 개별 텍스처를 교체할 수 있습니다. PNG는 표면 무늬를, 클라이언트 모델은 형태·위치·애니메이션·장비 표시 조건을 정의합니다.

## 장식 파일

모든 장식 아틀라스는 **256×256픽셀**이며, 모델의 **64×64 논리 UV 공간**에 대응합니다. UV 1단위는 이미지의 4픽셀입니다. 해상도를 바꿀 때는 정사각형 비율과 사용 영역을 유지하고, 도트 이미지의 크기는 최근접 이웃 방식으로 조정합니다. 각 영역 사이는 투명하며, 얇은 면에 틈이 생기지 않도록 가장자리에 불투명 여백을 둡니다.

| 파일 | 표시 요소 |
| --- | --- |
| `mining.png` | 채굴·터널 슬라임의 노란 안전모, 챙, 앞쪽 발광 램프. |
| `transport.png` | 운반·창고 슬라임의 나무 가방과 갈색 끈. |
| `farmer.png` | 농사 슬라임의 밀짚모자, 챙, 가죽 띠. |
| `lumberjack.png` | 벌목 슬라임의 가죽 모자와 챙. |
| `healing.png` | 치유·풍요 슬라임의 상아색 머리띠와 황금 십자가. |
| `cleaning.png` | 파란 작업모자, 챙, 금속 표식. |
| `combat.png` | 전투·드래곤 슬라임의 금속 머리띠와 붉은 문장. |
| `bridle.png` | 안장 슬라임의 갈색 굴레와 금색 버클. |
| `soggy.png` | 초록 점액 방울. |
| `fishing.png` | 낮게 내려온 파란 멜빵 바지, 끈, 금색 단추. |
| `abundance.png` | 옆에 든 황금 리라, 상아색 현, 초록 월계관. |
| `tunnel.png` | 금속 광산 수레와 어두운 바퀴. |
| `dragon.png` | 보라색 날개막과 어두운 뿔. |
| `commander.png` | 검은 콧수염, 나무 지휘봉, 금색 봉 끝. |
| `guardian_halo.png` | 빛나는 황금 고리. |
| `saddle.png` | 가죽 안장, 안장 끈, 금속 등자. |
| `armor_leather.png` | 원본 흉갑의 염색 색상을 곱하는 중립색 가죽 갑옷 표면. |
| `armor_chainmail.png` | 사슬 갑옷 표면. |
| `armor_iron.png` | 철 갑옷 표면. |
| `armor_golden.png` | 금 갑옷 표면. |
| `armor_diamond.png` | 다이아몬드 갑옷 표면. |
| `armor_netherite.png` | 네더라이트 갑옷 표면. |

갑옷 6종은 같은 UV 좌표와 착용 윤곽을 공유합니다. 깃털 날개와 파발 모자는 기존 `textures/entity/slime_wings.png`와 `herald_hat.png`를 사용합니다. 냥냥 슬라임은 Minecraft 기본 고양이 머리 모델과 텍스처를 사용합니다. 손에 든 도구는 원래 아이템 모델을 표시합니다.

## UV 배치

[`texture-layouts.json`](texture-layouts.json)에 각 아틀라스의 모델 클래스와 직육면체 영역을 정리합니다. `uv`는 직육면체 영역 왼쪽 위의 논리 좌표이며, `size`는 모델 단위의 너비·높이·깊이입니다. `cube`는 해당 모델 부품 안에서 1부터 세는 직육면체 순번입니다. 반복되는 리라 현과 좌우 드래곤 날개는 각각 같은 UV 영역을 공유합니다.

시작점이 `(u, v)`, 크기가 `(w, h, d)`인 직육면체의 두 수평 면은 `(u+d, v, w, d)`와 `(u+d+w, v, w, d)`를 차지합니다. 네 수직 면은 `v+d`에서 시작하는 한 줄에 깊이 `d`, 너비 `w`, 깊이 `d`, 너비 `w` 순서로 놓이며 높이는 `h`입니다. 기본 PNG에서 영역을 찾으려면 이 좌표에 4를 곱합니다. 소수 크기를 사용하는 얇은 부품은 가장자리의 불투명 여백을 유지합니다.

표면 수정은 **F3+T**로 리소스를 다시 불러오면 반영됩니다. UV 배치·입체 형태·부착 위치를 바꿀 때는 해당 클라이언트 모델도 수정합니다. 채굴 램프와 수호 고리의 최대 밝기, 가죽 염색, 갑옷 마법 부여 광택은 렌더링 시 적용합니다.

## 화면 파일

화면 배경은 [`assets/slimic/textures/gui`](../src/main/resources/assets/slimic/textures/gui)에 있습니다. 기본 이미지 크기는 화면 좌표와 정확히 대응합니다.

| 파일 | 크기 | 용도 |
| --- | --- | --- |
| `slime.png` | 368×240 | 동료 관리 패널과 제목 영역. |
| `creative_tuner.png` | 360×230 | 크리에이티브 튜너 패널과 제목 영역. |
| `commander.png` | 310×235 | 지휘 슬라임 명부 패널과 제목 영역. |
| `slots.png` | 36×18 | 왼쪽은 활성 슬롯 18×18, 오른쪽은 잠긴 가방 칸 18×18. |

슬롯 이미지에는 16×16 아이템 영역 둘레의 1픽셀 테두리가 포함됩니다. 관리 화면은 일부만 열린 가방 페이지를 포함하여 메뉴의 슬롯 좌표에 맞춰 이미지를 배치합니다. 문구·조작 버튼·선택한 행의 강조·도움말·아이템 내용은 실행 중에 표시합니다.

## 실제 클라이언트 검증

준비된 클라이언트 스모크 월드에서 다음과 같이 실행합니다.

```sh
JAVA_TOOL_OPTIONS="-Dslimic.accessoryTexturesOnly=true" ./gradlew runClient -PclientSmoke=true
JAVA_TOOL_OPTIONS="-Dslimic.companionTooltipsOnly=true" ./gradlew runClient -PclientSmoke=true
```

클라이언트 스모크 검증에 `-Dslimic.accessoryTexturesOnly=true`를 지정하면 불러온 PNG 크기와 Minecraft 모델의 실제 UV가 가리키는 면의 불투명도를 확인합니다. 이어서 스물두 직업과 갑옷 외형 8종의 앞뒤 모습을 촬영하며, 가죽 염색·몸체 염색·마법 부여 광택·장착 도구·날개를 포함합니다. 임시 512×512 리소스 팩 교체와 원래 256×256 GPU 텍스처 복원도 확인합니다. `-Dslimic.textureReloadCycles=100`을 추가하면 교체·복원 주기를 반복해 장시간 재로딩을 검사합니다. 기존 `-Dslimic.inventoryLayoutOnly=true` 검증은 동기화된 한·영 관리 화면 배치를 확인합니다. 두 옵션은 개발 가이드에 설명한 별도 클라이언트 스모크 월드를 사용합니다.
