# Huong dan cai dat va su dung AIProblemSolver.jar

## Yeu cau cai dat tren may nguoi dung
- Java 16 hoac moi hon (JDK hoac JRE).
- JavaFX runtime phu hop voi phien ban Java, neu JDK/JRE khong kem JavaFX.

Lua chon cai dat de chay duoc:
- Cach 1 (de nhat): Cai JDK co kem JavaFX (VD: Liberica Full JDK 16+).
- Cach 2: Cai JDK/JRE 16+ va cai them JavaFX SDK dung phien ban.

Kiem tra Java:

```powershell
java -version
```

## Cach chay file jar
1) Dat file `AIProblemSolver.jar` trong thu muc bat ky.
2) Mo terminal va chay:

```powershell
java -jar AIProblemSolver.jar
```

## Loi thuong gap
- Thong bao "JavaFX runtime components are missing":
  - Chua cai JavaFX runtime.
  - Giai phap: Cai JavaFX phu hop hoac dung ban dong goi kem runtime (neu co).

## Ghi chu
- Neu ung dung khong chay, vui long cung cap log console de kiem tra.
