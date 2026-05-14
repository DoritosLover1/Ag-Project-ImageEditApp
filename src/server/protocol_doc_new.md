```
PaiCollab Messaging Protocol                                  PCMP/2.1
Kategori: Uygulama Katmanı Protokolü                       Mayıs 2025
```

---

# PaiCollab Messaging Protocol (PCMP) Sürüm 2.1

## Özet

Bu belge, PaiCollab işbirlikçi çizim ve mesajlaşma uygulamasında sunucu ile
istemciler arasındaki iletişimi tanımlar. Herhangi bir programlama dili veya
platformda bu belgeye uygun bir istemci veya sunucu uygulaması geliştirilebilir.
Bu belge dilden ve platformdan bağımsızdır.

---

## İçindekiler

1. Taşıma Katmanı
2. Mesaj Yapısı (Envelope)
3. Durum Makinası (FSM)
4. Mesaj Tipleri ve Alındığında Yapılacak İşlemler
   - 4.1 Oturum Yönetimi
   - 4.2 Oda Yönetimi
   - 4.3 Çizim Komutları
   - 4.4 Mesajlaşma
5. Broadcast Kuralları
6. Oda Girişi Akış Sırası
7. Veri Tipleri ve Kısıtlamalar

---

## 1. Taşıma Katmanı

PCMP, güvenilir, sıralı ve akış tabanlı iletişim sağlamak amacıyla TCP üzerinde
çalışır.

| Özellik        | Değer                      |
| :------------- | :------------------------- |
| Protokol       | TCP                        |
| Varsayılan Port| 12345                      |
| Karakter Seti  | UTF-8                      |
| Mesaj Sonu     | LF (0x0A)                  |
| Alan Ayırıcı   | Dikey Çizgi / Pipe (0x7C)  |

Her mesaj tek bir satırdan oluşur ve satır sonu karakteri (LF) ile sonlandırılır.
Alıcı taraf, gelen veri akışını LF karakterine göre bölerek mesajları birbirinden
ayırır.

---

## 2. Mesaj Yapısı (Envelope)

Her mesaj, dört adet sabit başlık alanı ve ardından komuta özgü veri alanlarından
oluşur. Tüm alanlar pipe karakteri ile ayrılır.

```
MESAJ_KÜMESI:
  [MESSAGE_ID] | [TIMESTAMP] | [SENDER] | [COMMAND] | [DATA_1] | [DATA_2] | ...
      0               1            2           3           4           5
```

| Alan        | Konum | Tip    | Açıklama                                        |
| :---------- | :---- | :----- | :---------------------------------------------- |
| MESSAGE_ID  | 0     | Metin  | 8 karakterlik benzersiz mesaj tanımlayıcısı     |
| TIMESTAMP   | 1     | Sayı   | Unix epoch milisaniye cinsinden zaman damgası   |
| SENDER      | 2     | Metin  | Gönderenin kullanıcı adı veya SERVER sabit değeri |
| COMMAND     | 3     | Metin  | Büyük harflerle yazılmış komut adı              |
| DATA_N      | 4+    | Değişken | Komuta özgü parametreler                      |

Sunucudan gelen tüm mesajlarda SENDER alanı `SERVER` değerini taşır. İstemciden
gelen mesajlarda bu alan istemcinin kullanıcı adıdır.

---

## 3. Durum Makinası (FSM)

Bir istemci bağlantısı boyunca aşağıdaki beş durumdan birinde bulunur:

```
         ┌──────────────┐
         │ DISCONNECTED │
         └──────┬───────┘
                │ TCP bağlantısı kurulur
                ▼
         ┌──────────────┐
         │  CONNECTED   │──── LOGIN gönderilir ────►┌────────────────┐
         └──────────────┘                           │ AUTHENTICATING │
                                                    └───────┬────────┘
                                                            │ LOGIN_SUCCESS alınır
                                                            ▼
                                               ┌────────────────────┐
                                    ┌─────────►│       LOBBY        │◄────────┐
                                    │          └──────────┬──────────┘        │
                                    │                     │ CREATE_ROOM       │
                                    │                     │ veya JOIN_ROOM    │
                                    │                     ▼                   │
                                    │          ┌────────────────────┐         │
                                    └──────────│       ROOM         │─────────┘
                              LEAVE_ROOM alınır└────────────────────┘ LEAVE_ROOM
                                              CHAT / DRAW / CURSOR işlemleri
```

| Durum          | Açıklama                                                         |
| :------------- | :--------------------------------------------------------------- |
| DISCONNECTED   | TCP bağlantısı henüz kurulmamış veya kesilmiş                    |
| CONNECTED      | TCP bağlantısı kurulmuş, kimlik doğrulaması yapılmamış           |
| AUTHENTICATING | LOGIN mesajı gönderilmiş, sunucu yanıtı bekleniyor               |
| LOBBY          | Kimlik doğrulaması tamamlanmış, herhangi bir odada değil         |
| ROOM           | Bir odaya katılmış, çizim ve mesajlaşma işlemleri aktif          |

---

## 4. Mesaj Tipleri ve Alındığında Yapılacak İşlemler

### 4.1 Oturum Yönetimi

---

#### LOGIN
**Yön:** İstemci → Sunucu

Sunucuya kullanıcı adı ile giriş isteği gönderir. İstemci CONNECTED durumunda
bu mesajı gönderir.

| Konum | Alan     | Tip   | Açıklama              |
| :---- | :------- | :---- | :-------------------- |
| 4     | username | Metin | İstenen kullanıcı adı |

**Sunucu Yanıtı:** LOGIN_SUCCESS veya ERROR

---

#### LOGIN_SUCCESS
**Yön:** Sunucu → İstemci

Giriş isteğinin başarıyla kabul edildiğini bildirir.

| Konum | Alan     | Tip   | Açıklama              |
| :---- | :------- | :---- | :-------------------- |
| 4     | nickname | Metin | Onaylanan kullanıcı adı |

**İstemci, bu mesajı aldığında:**
- Onaylanan kullanıcı adını yerel olarak saklar
- LOBBY durumuna geçer ve oda seçim ekranını gösterir

---

#### ERROR
**Yön:** Sunucu → İstemci

Bir işlemin başarısız olduğunu bildirir.

| Konum | Alan          | Tip   | Açıklama    |
| :---- | :------------ | :---- | :---------- |
| 4     | error_message | Metin | Hata açıklaması |

**İstemci, bu mesajı aldığında:**
- Hata mesajını kullanıcıya gösterir
- Durum değişikliği yapmaz

---

### 4.2 Oda Yönetimi

---

#### CREATE_ROOM
**Yön:** İstemci → Sunucu

Yeni bir oda oluşturma isteği gönderir. İstemci LOBBY durumunda bu mesajı
gönderebilir.

| Konum | Alan | Tip   | Açıklama              |
| :---- | :--- | :---- | :-------------------- |
| 4     | —    | Metin | Sabit değer: `NEW`    |

**Sunucu Yanıtı:** ROOM_INFO ardından snapshot dizisi

---

#### JOIN_ROOM
**Yön:** İstemci → Sunucu

Var olan bir odaya katılma isteği gönderir. İstemci LOBBY durumunda bu mesajı
gönderebilir.

| Konum | Alan     | Tip   | Açıklama                          |
| :---- | :------- | :---- | :-------------------------------- |
| 4     | roomCode | Metin | 6 karakterlik oda kodu            |

**Sunucu Yanıtı:** ROOM_INFO ardından snapshot dizisi veya ERROR

---

#### ROOM_INFO
**Yön:** Sunucu → İstemci

Odaya giriş işleminin onaylandığını bildirir. Snapshot dizisinin başlangıcını
işaret eder.

| Konum | Alan     | Tip   | Açıklama         |
| :---- | :------- | :---- | :--------------- |
| 4     | roomCode | Metin | Girilen odanın kodu |

**İstemci, bu mesajı aldığında:**
- Çizim alanını tamamen temizler
- Sohbet geçmişini temizler
- Tüm uzak imleçleri ekrandan kaldırır
- ROOM durumuna geçer
- Ardından gelen snapshot mesajlarını almaya hazırlanır

---

#### LEAVE_ROOM
**Yön:** İstemci → Sunucu

Bulunulan odadan ayrılma isteği gönderir. İstemci ROOM durumunda bu mesajı
gönderebilir.

| Konum | Alan | Tip   | Açıklama              |
| :---- | :--- | :---- | :-------------------- |
| 4     | —    | Metin | Sabit değer: `LEAVE`  |

**Sunucu:** İstemciyi odadan çıkarır, kalan üyelere güncel USER_LIST gönderir.
**İstemci:** LOBBY durumuna döner.

---

#### QUIT
**Yön:** İstemci → Sunucu

Uygulamayı kapatmadan önce bağlantının düzgün sonlandırılmasını sağlar.

| Konum | Alan | Tip   | Açıklama              |
| :---- | :--- | :---- | :-------------------- |
| 4     | —    | Metin | Sabit değer: `QUIT`   |

**Sunucu:** İstemciyi odadan ve sistemden çıkarır, TCP bağlantısını kapatır.

---

#### USER_LIST
**Yön:** Sunucu → Odadaki Tüm Üyeler

Odanın güncel üye listesini bildirir. Bir kullanıcı odaya her katıldığında veya
ayrıldığında otomatik olarak gönderilir.

| Konum | Alan  | Tip   | Açıklama                              |
| :---- | :---- | :---- | :------------------------------------ |
| 4     | users | Metin | Virgülle ayrılmış kullanıcı adları listesi |

**İstemci, bu mesajı aldığında:**
- Üye listesini günceller
- Listede artık yer almayan kullanıcıların imlecini ekrandan kaldırır

---

#### NEW_USERNAME
**Yön:** İstemci → Sunucu

Kullanıcı adını değiştirme isteği gönderir.

| Konum | Alan    | Tip   | Açıklama              |
| :---- | :------ | :---- | :-------------------- |
| 4     | oldNick | Metin | Mevcut kullanıcı adı  |
| 5     | newNick | Metin | İstenen yeni kullanıcı adı |

**Sunucu Yanıtı:** NAME_CHANGED veya ERROR

---

#### NAME_CHANGED
**Yön:** Sunucu → İstemci

Kullanıcı adı değiştirme isteğinin kabul edildiğini bildirir.

| Konum | Alan    | Tip   | Açıklama                    |
| :---- | :------ | :---- | :-------------------------- |
| 4     | newNick | Metin | Onaylanan yeni kullanıcı adı |

**İstemci, bu mesajı aldığında:**
- Yerel kullanıcı adını günceller
- Sunucuya gönderilecek sonraki mesajlarda yeni adı kullanır
- Odadaki diğer üyelere güncel USER_LIST sunucu tarafından iletilir

---

### 4.3 Çizim Komutları

Tüm çizim komutları ROOM durumundayken gönderilebilir ve alınabilir. Her çizim
nesnesi istemci tarafında üretilen ve ağ genelinde benzersiz olan bir kimlik
değeri (UUID) taşır.

Tüm renk değerleri `#RRGGBB` biçiminde yedi karakterlik onaltılık sayı
dizisidir.

---

#### SQUARE — Dikdörtgen
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan   | Tip     | Açıklama                    |
| :---- | :----- | :------ | :-------------------------- |
| 4     | x      | Tam sayı | Sol üst köşe X koordinatı  |
| 5     | y      | Tam sayı | Sol üst köşe Y koordinatı  |
| 6     | w      | Tam sayı | Genişlik (piksel)           |
| 7     | h      | Tam sayı | Yükseklik (piksel)          |
| 8     | color  | Metin   | Dolgu veya kenar rengi      |
| 9     | stroke | Tam sayı | Kenar kalınlığı (piksel)    |
| 10    | filled | Mantıksal| Dolu mu? (`true` / `false`) |
| 11    | id     | Metin   | Nesnenin benzersiz kimliği  |

**İstemci, bu mesajı aldığında:**
- Kimliği daha önce eklenmemiş ise dikdörtgeni çizim alanına ekler
- Kimlik zaten varsa mesajı yok sayar (mükerrer koruma)
- Nesneyi sunucunun sakladığı snapshot listesiyle eşleşecek şekilde yerel listeye ekler

---

#### CIRCLE — Elips
**Yön:** İstemci → Sunucu → Diğer İstemciler

Alan yapısı SQUARE ile özdeştir; nesne dikdörtgen sınırlayıcı kutu içine sığan
elips olarak çizilir.

| Konum | Alan   | Tip      | Açıklama                              |
| :---- | :----- | :------- | :------------------------------------ |
| 4     | x      | Tam sayı | Sınırlayıcı kutunun sol üst köşesi X  |
| 5     | y      | Tam sayı | Sınırlayıcı kutunun sol üst köşesi Y  |
| 6     | w      | Tam sayı | Genişlik (piksel)                     |
| 7     | h      | Tam sayı | Yükseklik (piksel)                    |
| 8     | color  | Metin    | Dolgu veya kenar rengi                |
| 9     | stroke | Tam sayı | Kenar kalınlığı (piksel)              |
| 10    | filled | Mantıksal| Dolu mu? (`true` / `false`)           |
| 11    | id     | Metin    | Nesnenin benzersiz kimliği            |

**İstemci, bu mesajı aldığında:** SQUARE ile aynı işlem uygulanır.

---

#### LINE — Çizgi
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan   | Tip      | Açıklama                    |
| :---- | :----- | :------- | :-------------------------- |
| 4     | x1     | Tam sayı | Başlangıç noktası X         |
| 5     | y1     | Tam sayı | Başlangıç noktası Y         |
| 6     | x2     | Tam sayı | Bitiş noktası X             |
| 7     | y2     | Tam sayı | Bitiş noktası Y             |
| 8     | color  | Metin    | Çizgi rengi                 |
| 9     | stroke | Tam sayı | Çizgi kalınlığı (piksel)    |
| 10    | id     | Metin    | Nesnenin benzersiz kimliği  |

**İstemci, bu mesajı aldığında:** SQUARE ile aynı mükerrer koruma uygulanır.

---

#### TRIANGLE — Üçgen
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan   | Tip      | Açıklama                    |
| :---- | :----- | :------- | :-------------------------- |
| 4     | x1     | Tam sayı | 1. köşe X                   |
| 5     | y1     | Tam sayı | 1. köşe Y                   |
| 6     | x2     | Tam sayı | 2. köşe X                   |
| 7     | y2     | Tam sayı | 2. köşe Y                   |
| 8     | x3     | Tam sayı | 3. köşe X                   |
| 9     | y3     | Tam sayı | 3. köşe Y                   |
| 10    | color  | Metin    | Dolgu veya kenar rengi      |
| 11    | stroke | Tam sayı | Kenar kalınlığı (piksel)    |
| 12    | filled | Mantıksal| Dolu mu? (`true` / `false`) |
| 13    | id     | Metin    | Nesnenin benzersiz kimliği  |

**İstemci, bu mesajı aldığında:** SQUARE ile aynı mükerrer koruma uygulanır.

---

#### FREEHAND — Serbest Çizim
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan     | Tip      | Açıklama                                              |
| :---- | :------- | :------- | :---------------------------------------------------- |
| 4     | x_points | Metin    | Virgülle ayrılmış X koordinatları dizisi              |
| 5     | y_points | Metin    | Virgülle ayrılmış Y koordinatları dizisi              |
| 6     | color    | Metin    | Çizgi rengi                                           |
| 7     | stroke   | Tam sayı | Çizgi kalınlığı (piksel)                              |
| 8     | id       | Metin    | Nesnenin benzersiz kimliği                            |

x_points ve y_points dizileri aynı uzunlukta olmalı, her indeks bir koordinat
çiftini temsil eder.

**İstemci, bu mesajı aldığında:** SQUARE ile aynı mükerrer koruma uygulanır.

---

#### IMAGE — Yapıştırılan Görsel
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan        | Tip      | Açıklama                                   |
| :---- | :---------- | :------- | :----------------------------------------- |
| 4     | x           | Tam sayı | Görselin sol üst köşesi X                  |
| 5     | y           | Tam sayı | Görselin sol üst köşesi Y                  |
| 6     | w           | Tam sayı | Genişlik (piksel)                          |
| 7     | h           | Tam sayı | Yükseklik (piksel)                         |
| 8     | base64_data | Metin    | PNG verisinin Base64 kodlanmış hali        |
| 9     | id          | Metin    | Nesnenin benzersiz kimliği                 |

Görsel verisi RFC 4648 standardına uygun Base64 ile kodlanır.

**İstemci, bu mesajı aldığında:**
- Kimliği daha önce eklenmemiş ise Base64 verisini çözer ve görseli belirtilen
  konumda çizim alanına yerleştirir
- Kimlik zaten varsa mesajı yok sayar

---

#### DELETE — Nesne Sil
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan     | Tip   | Açıklama                    |
| :---- | :------- | :---- | :-------------------------- |
| 4     | targetId | Metin | Silinecek nesnenin kimliği  |

**İstemci, bu mesajı aldığında:**
- Belirtilen kimliğe sahip nesneyi çizim alanından kaldırır
- Nesne yerel listede bulunamazsa mesaj sessizce yok sayılır

---

#### CLEAR — Tümünü Temizle
**Yön:** İstemci → Sunucu → Tüm Üyeler (Gönderen Dahil)

| Konum | Alan | Tip   | Açıklama             |
| :---- | :--- | :---- | :------------------- |
| 4     | —    | Metin | Sabit değer: `ALL`   |

**İstemci, bu mesajı aldığında:**
- Çizim alanındaki tüm nesneleri siler
- Yerel nesne listesini temizler
- Gönderen istemci de bu mesajı alır (bkz. Bölüm 5)

---

#### CURSOR — İmleç Konumu
**Yön:** İstemci → Sunucu → Diğer İstemciler

| Konum | Alan  | Tip      | Açıklama                    |
| :---- | :---- | :------- | :-------------------------- |
| 4     | x     | Tam sayı | İmleç X koordinatı          |
| 5     | y     | Tam sayı | İmleç Y koordinatı          |
| 6     | color | Metin    | İmleç rengi                 |

CURSOR mesajları sunucuda saklanmaz. Anlık konum bilgisi aktarımı için kullanılır.
Yüksek frekanslı fare hareketlerini ağda kısıtlamak istemcinin sorumluluğundadır.

**İstemci, bu mesajı aldığında:**
- İlgili kullanıcının uzak imleç göstergesini belirtilen konuma taşır
- Kullanıcı daha önce görünmüyorsa imleç göstergesini oluşturur

---

### 4.4 Mesajlaşma

---

#### CHAT — Anlık Mesaj
**Yön:** İstemci → Sunucu → Tüm Üyeler (Gönderen Dahil)

| Konum | Alan    | Tip   | Açıklama                  |
| :---- | :------ | :---- | :------------------------ |
| 4     | message | Metin | Mesaj içeriği (düz metin) |

Sunucu mesajı odadaki tüm üyelere iletir ve oda geçmişine kaydeder.

**İstemci, bu mesajı aldığında:**
- Mesajı sohbet alanında göndericinin adı ve zaman damgasıyla birlikte gösterir

---

#### CHAT_HISTORY — Geçmiş Mesaj
**Yön:** Sunucu → İstemci (Yalnızca Oda Girişinde)

Odaya giriş sırasında daha önce gönderilmiş mesajları aktarmak için kullanılır.
Mesaj içeriği pipe ve satır sonu karakterleri içerebileceğinden Base64 ile
kodlanır.

| Konum | Alan              | Tip   | Açıklama                                      |
| :---- | :---------------- | :---- | :-------------------------------------------- |
| 4     | originalSender    | Metin | Mesajı ilk gönderen kullanıcının adı          |
| 5     | base64_message    | Metin | Mesaj içeriğinin Base64 kodlanmış hali (UTF-8)|
| 6     | originalTimestamp | Sayı  | Mesajın ilk gönderildiği zaman (epoch ms)     |

**İstemci, bu mesajı aldığında:**
- Base64 kodlu içeriği çözer
- Mesajı özgün gönderici adı ve özgün zaman damgasıyla sohbet alanına ekler
- Bu mesaj anlık değil geçmiş mesaj olarak işaretlenerek görüntülenir

---

## 5. Broadcast Kuralları

Sunucu her mesaj tipini belirli bir dağıtım kuralına göre iletir.

| Komut                                             | Gönderene İletilir mi? | Sunucuda Saklanır mı?         |
| :------------------------------------------------ | :--------------------- | :---------------------------- |
| SQUARE / CIRCLE / LINE / TRIANGLE / FREEHAND      | Hayır                  | Evet                          |
| IMAGE                                             | Hayır                  | Evet                          |
| DELETE                                            | Hayır                  | Evet (ilgili nesne silinir)   |
| CLEAR                                             | Evet                   | Evet (tüm nesneler silinir)   |
| CURSOR                                            | Hayır                  | Hayır                         |
| CHAT                                              | Evet                   | Evet                          |
| USER_LIST                                         | Evet                   | Hayır                         |
| LOGIN_SUCCESS / ERROR / NAME_CHANGED / ROOM_INFO  | Yalnızca hedef istemci | —                             |

---

## 6. Oda Girişi Akış Sırası (Snapshot)

Bir istemci CREATE_ROOM veya JOIN_ROOM gönderdiğinde sunucu aşağıdaki sırayı
izler. İstemci bu sıralamayı göz önünde bulundurarak snapshot mesajlarını işler.

```
Adım  Mesaj Tipi          İstemci Eylemi
----  ------------------  ------------------------------------------------
  1   ROOM_INFO           Canvas ve sohbet temizlenir, tüm imleçler silinir
  2   USER_LIST           Üye listesi güncellenir
  3   SQUARE/CIRCLE/...   Mevcut çizim nesneleri birer birer canvas'a eklenir
  4   IMAGE               Mevcut görseller birer birer canvas'a eklenir
  5   CHAT_HISTORY        Geçmiş mesajlar birer birer sohbet alanına eklenir
```

3, 4 ve 5. adımlardaki mesaj sayısı odanın mevcut içeriğine göre sıfır veya
daha fazla olabilir. İstemci her nesneyi kimliğine göre kontrol ederek aynı
nesneyi iki kez eklememelidir.

---

## 7. Veri Tipleri ve Kısıtlamalar

| Veri Tipi   | Açıklama                                                             |
| :---------- | :------------------------------------------------------------------- |
| Metin       | UTF-8 kodlu karakter dizisi; pipe ve satır sonu karakteri içeremez  |
| Tam sayı    | İşaretli 32-bit tamsayı aralığında ondalıksız sayı                  |
| Sayı (long) | İşaretli 64-bit tamsayı; Unix epoch milisaniye zaman damgaları için |
| Mantıksal   | Yalnızca `true` veya `false` değerini alır                          |
| Base64      | RFC 4648 standardı; standart alfabe, dolgu karakteri dahil           |

| Sabit            | Biçim                                | Örnek         |
| :--------------- | :----------------------------------- | :------------ |
| Oda Kodu         | 6 karakterlik büyük harf ve rakam    | `ABC123`      |
| Renk             | 7 karakterlik onaltılık renk kodu    | `#FF0000`     |
| Nesne Kimliği    | Evrensel benzersiz tanımlayıcı (UUID)| `a1b2c3d4...` |
| MESSAGE_ID       | UUID'nin ilk 8 karakteri             | `a1b2c3d4`    |

Mesaj içeriğinde pipe karakteri (`|`) kullanılması gerektiğinde ilgili alan
Base64 ile kodlanmalıdır. Bu nedenle CHAT_HISTORY mesajlarında mesaj içeriği
zorunlu olarak Base64 kodludur.