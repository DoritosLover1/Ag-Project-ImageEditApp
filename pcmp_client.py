import socket
import threading
import time
import base64

# Sunucu Bilgileri
HOST = '127.0.0.1'
PORT = 12345
USERNAME = "Python_Ressam"

def receive_messages(sock):
    """Sunucudan gelen mesajları dinleyen thread fonksiyonu"""
    buffer = ""
    while True:
        try:
            data = sock.recv(4096).decode('utf-8')
            if not data:
                break
            buffer += data
            while "\n" in buffer:
                line, buffer = buffer.split("\n", 1)
                process_message(line)
        except Exception as e:
            print(f"Hata: {e}")
            break

def process_message(raw_msg):
    """Gelen ham mesajı PCMP protokolüne göre ayrıştırır"""
    parts = raw_msg.split("|")
    if len(parts) < 4:
        return

    sender = parts[2]
    command = parts[3]
    
    if command == "CHAT":
        print(f"\n[SOHBET] {sender}: {parts[4]}")
    elif command == "SQUARE":
        print(f"[CIZIM] {sender} bir KARE çizdi: x={parts[4]}, y={parts[5]}")
    elif command == "IMAGE":
        print(f"[RESIM] {sender} bir RESIM yapıştırdı! (ID: {parts[9]})")
    elif command == "ROOM_INFO":
        print(f"\n*** {parts[4]} numaralı odaya başarıyla girdiniz! ***\n")

def main():
    # Soket oluştur ve bağlan
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        try:
            s.connect((HOST, PORT))
            print(f"Sunucuya bağlandı: {HOST}:{PORT}")

            # 1. Login ol (Protokol: ID|Timestamp|Sender|Command|Data)
            login_msg = f"p_001|{int(time.time())}|{USERNAME}|LOGIN|{USERNAME}\n"
            s.sendall(login_msg.encode('utf-8'))

            # Dinlemeyi başlat
            threading.Thread(target=receive_messages, args=(s,), daemon=True).start()

            # 2. Bir odaya gir (veya oluştur)
            room_code = input("Girmek istediğiniz oda kodu: ").upper()
            join_msg = f"p_002|{int(time.time())}|{USERNAME}|JOIN_ROOM|{room_code}\n"
            s.sendall(join_msg.encode('utf-8'))

            print("Komutlar: 'msg <metin>' (Sohbet), 'quit' (Çıkış)")
            while True:
                user_input = input("> ")
                if user_input.lower() == 'quit':
                    break
                elif user_input.startswith("msg "):
                    msg_text = user_input[4:]
                    chat_msg = f"p_msg|{int(time.time())}|{USERNAME}|CHAT|{msg_text}\n"
                    s.sendall(chat_msg.encode('utf-8'))
                
        except Exception as e:
            print(f"Bağlantı hatası: {e}")

if __name__ == "__main__":
    main()
