package network;

import javax.sound.sampled.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VoiceChatManager {

    private TargetDataLine mic;
    private SourceDataLine speaker;

    private Thread captureThread;
    private boolean isCapturing = false;

    // Susturulmuş (muted) kullanıcı adlarını tutar (küçük harflerle)
    private final Set<String> mutedUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private Consumer<byte[]> onAudioCaptured;

    public VoiceChatManager() {
        // Default constructor
    }

    public void setOnAudioCaptured(Consumer<byte[]> onAudioCaptured) {
        this.onAudioCaptured = onAudioCaptured;
    }

    public void toggleMuteUser(String username) {
        if (username == null)
            return;
        String key = username.toLowerCase();
        if (mutedUsers.contains(key)) {
            mutedUsers.remove(key);
        } else {
            mutedUsers.add(key);
        }
    }

    public boolean isUserMuted(String username) {
        if (username == null)
            return false;
        return mutedUsers.contains(username.toLowerCase());
    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 8000.0F; // 8kHz, 16 bit, mono, signed, little-endian
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = false;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }

    public void start(Mixer.Info inputMixerInfo, Mixer.Info outputMixerInfo) throws LineUnavailableException {
        stop(); // Ensure everything is stopped

        AudioFormat format = getAudioFormat();

        // 1. Setup Input (Microphone)
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
        if (inputMixerInfo != null) {
            mic = (TargetDataLine) AudioSystem.getMixer(inputMixerInfo).getLine(micInfo);
        } else {
            mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        }
        mic.open(format);
        mic.start();

        // 2. Setup Output (Speaker)
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
        if (outputMixerInfo != null) {
            speaker = (SourceDataLine) AudioSystem.getMixer(outputMixerInfo).getLine(speakerInfo);
        } else {
            speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        }
        speaker.open(format);
        speaker.start();

        isCapturing = true;
        captureThread = new Thread(() -> {
            byte[] buffer = new byte[1024]; // Small buffer for low latency
            while (isCapturing) {
                int bytesRead = mic.read(buffer, 0, buffer.length);
                if (bytesRead > 0 && onAudioCaptured != null) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    onAudioCaptured.accept(data);
                }
            }
        });
        captureThread.start();
    }

    public void stop() {
        isCapturing = false;
        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException ignored) {
            }
        }
        if (mic != null) {
            mic.stop();
            mic.close();
            mic = null;
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
            speaker = null;
        }
    }

    // Gelen sesi çalar
    public void playAudio(String sender, byte[] audioData) {
        if (speaker == null || !speaker.isOpen())
            return;
        if (isUserMuted(sender))
            return; // Sessize alınmışsa çalma

        speaker.write(audioData, 0, audioData.length);
    }

    // Ses seviyesi kontrolü (0.0f - 1.0f)
    public void setOutputVolume(float volume) {
        if (speaker != null && speaker.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gainControl = (FloatControl) speaker.getControl(FloatControl.Type.MASTER_GAIN);
            float range = gainControl.getMaximum() - gainControl.getMinimum();
            float gain = (range * volume) + gainControl.getMinimum();
            gainControl.setValue(gain);
        }
    }
}
