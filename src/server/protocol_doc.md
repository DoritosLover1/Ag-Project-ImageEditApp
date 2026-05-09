# RFC 001: PaiCollab Messaging Protocol (PCMP) - Teknik Uygulama Rehberi

Bu bölüm, `ClientNetworkManager` işlevselliğini sıfırdan kodlamak isteyen geliştiriciler için metodolojik bir referanstır.

---

## 1. Mesajlaşma Temelleri

Tüm iletişim tek satırlık `UTF-8` metinleridir. Sunucu her mesajı `\n` karakteri ile sonlandırılmış olarak bekler ve gönderir.

### 1.1. Mesaj Alanları
`ID | TIMESTAMP | SENDER | COMMAND | DATA_FIELDS...`
- `SENDER`: Mesajı başlatan istemcinin adıdır. Sunucu mesajlarında her zaman `SERVER` olur.
- `COMMAND`: Uygulama mantığını belirleyen anahtardır.

---

## 2. Metot ve Mesaj Eşleşmeleri (Client -> Server)

Aşağıdaki tablo, istemci tarafındaki fonksiyonların sunucuya ne gönderdiğini açıklar:

| İstemci Fonksiyonu | Gönderilen Mesaj (Format) | Beklenen Sunucu Tepkisi |
| :--- | :--- | :--- |
| `login(name)` | `...|LOGIN|name` | `LOGIN_SUCCESS` veya `ERROR` |
| `createRoom()` | `...|CREATE_ROOM|NEW` | `ROOM_INFO` (Yeni Kod) |
| `joinRoom(code)` | `...|JOIN_ROOM|code` | `ROOM_INFO` + Çizim Geçmişi |
| `leaveRoom()` | `...|LEAVE_ROOM|LEAVE` | (Sessiz ayrılma, odadakilere `USER_LIST`) |
| `changeName(new)` | `...|NEW_USERNAME|old|new` | `NAME_CHANGED` |
| `sendShape(shape)` | `...|SHAPE_TYPE|X|Y|...|ID` | Odadakilere Broadcast |
| `sendCursor(cp)` | `...|CURSOR|X|Y|Color` | Odadakilere Broadcast (Filtreli*) |
| `sendClear()` | `...|CLEAR|ALL` | Odadakilere Broadcast |
| `sendDelete(id)` | `...|DELETE|id` | Odadakilere Broadcast |

*\*Not: Sunucu, CURSOR mesajlarını gönderen hariç herkese iletir.*

---

## 3. Sunucudan Gelen Mesajların İşlenmesi (Server -> Client)

İstemci, sunucudan gelen her mesajı dinlemeli ve `COMMAND` tipine göre şu aksiyonları almalıdır:

### 3.1. Yönetimsel Mesajlar
- **`LOGIN_SUCCESS`**: İstemci bu mesajı aldığında "Lobby" ekranına geçiş yapmalıdır.
- **`ROOM_INFO`**: İstemci bu mesajı aldığında "Canvas" ekranına geçiş yapmalı ve oda kodunu saklamalıdır.
- **`USER_LIST`**: Veri kısmındaki CSV listesini (`Ali,Veli...`) parçalayarak kullanıcı paneli arayüzünü güncellemelidir.
- **`ERROR`**: Gelen metni kullanıcıya "Hata" olarak göstermelidir.

### 3.2. Çizim Mesajları (Broadcast)
İstemci, kendi `SENDER` adı ile eşleşmeyen (başkalarından gelen) şu mesajları alabilir:
- **`SQUARE`, `CIRCLE`, `LINE` vb.**: Gelen geometrik verileri anında kendi `DrawingCanvas` nesnesine eklemeli ve çizmelidir.
- **`CURSOR`**: Gelen X, Y ve Renk verisiyle, ilgili kullanıcıya ait "hayalet imleci" (Remote Cursor) güncellemelidir.
- **`DELETE`**: ID'si belirtilen nesneyi kendi hafızasından ve ekranından silmelidir.
- **`CLEAR`**: Kendi ekranındaki tüm çizimleri temizlemelidir.

---

## 4. Detaylı Parametre Tipleri

| Komut | Parametre Detayı (DATA_FIELDS) |
| :--- | :--- |
| **SQUARE** | `X (int) | Y (int) | W (int) | H (int) | Color (Hex) | Stroke (int) | Filled (bool) | ID (string)` |
| **FREEHAND** | `X_list (csv) | Y_list (csv) | Color (Hex) | Stroke (int) | ID (string)` |
| **IMAGE** | `X (int) | Y (int) | W (int) | H (int) | ImageData (Base64) | ID (string)` |
| **NAME_CHANGED** | `NewNickname (string)` |

---

## 5. Örnek Senaryo: Odaya Katılma ve Çizim

1. **C:** `...|JOIN_ROOM|A1B2` (Odaya girme isteği)
2. **S:** `...|SERVER|ROOM_INFO|A1B2` (Onay, odaya geç)
3. **S:** `...|SERVER|SQUARE|...` (Sunucudaki 1. eski nesne)
4. **S:** `...|SERVER|LINE|...` (Sunucudaki 2. eski nesne)
5. **S:** `...|SERVER|USER_LIST|Alice,Bob,Mert` (Mevcut kullanıcılar)
6. **C:** `...|Mert|SQUARE|50|50|...|rect_99` (Mert yeni bir kare çizer)
7. **S:** (Bu kare mesajını Alice ve Bob'a iletir)
