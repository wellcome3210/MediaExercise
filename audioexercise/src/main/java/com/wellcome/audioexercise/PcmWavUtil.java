package com.wellcome.audioexercise;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class PcmWavUtil {
    public static final int INDEX_CHUNK_SIZE = 4;
    public static final int INDEX_FCC_TYPE = 8;
    public static final int INDEX_SUB_CHUNKSIZE = 16;
    public static final int INDEX_CHANNELS = 22;
    public static final int INDEX_SAMPLES_PER_SEC = 24;
    public static final int INDEX_BYTES_PER_SEC = 28;
    public static final int INDEX_BLOCK_ALIGN = 32;
    public static final int INDEX_BITS_PER_SAMPLE = 34;
    public static final int INDEX_SUB_CHUNK_SIZE_2 = 40;
    static final byte[] HEAD = new byte[]{
            'R', 'I', 'F', 'F',             // 00-03, ChunkId
            0, 0, 0, 0,                     // 04-07, ChunkSize
            'W', 'A', 'V', 'E',             // 08-11, fccType
            'f', 'm', 't', ' ',             // 12-15, SubChunkId1
            16, 0, 0, 0,                    // 16-19, SubChunkSize1
            1, 0,                           // 20-21, FormatTag
            1, 0,                           // 22-23, Channels
            0, 0, 0, 0,                     // 24-27, SamplesPerSec
            0, 0, 0, 0,                     // 28-31, BytesPerSec
            0, 0,                           // 32-33, BlockAlign, BitsPerSample * Channels / 8
            0, 0,                           // 34-35, BitsPerSample
            'd', 'a', 't', 'a',             // 36-39, SubChunkId2
            0, 0, 0, 0                      // 40-43, SubChunkSize2
    };

    public static String pcm2Wav(int channels, int samplesPerSec, int bitsPerSample, File file) throws IOException {
        byte[] head = generateWavHead(channels, samplesPerSec, bitsPerSample, file.length());
        String newFileName = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf('.') + 1) + "wav";
        FileInputStream fileInputStream = new FileInputStream(file);
        FileOutputStream fileOutputStream = new FileOutputStream(new File(newFileName));
        fileOutputStream.write(head, 0, head.length);
        byte[] buf = new byte[1024];
        int length;
        while((length = fileInputStream.read(buf, 0, buf.length)) != -1){
            fileOutputStream.write(buf, 0, length);
        }
        fileOutputStream.flush();
        fileOutputStream.close();
        fileInputStream.close();
        return newFileName;
    }

    static class WavHeader{
        int chunkSize;
        int channels;
        int samplesPerSec;
        int bytesPerSec;
        int blockAlign;
        int bitsPerSample;
        int subChunkSize2;
    }

    public static WavHeader transformToWavHeader(byte[] buf) {
        WavHeader wavHeader = new WavHeader();
        wavHeader.chunkSize = readLittleEndianInt(buf, INDEX_CHUNK_SIZE, 4);
        wavHeader.channels = readLittleEndianInt(buf, INDEX_CHANNELS, 2);
        wavHeader.samplesPerSec = readLittleEndianInt(buf, INDEX_SAMPLES_PER_SEC, 4);
        wavHeader.bytesPerSec = readLittleEndianInt(buf, INDEX_BYTES_PER_SEC, 4);
        wavHeader.blockAlign = readLittleEndianInt(buf, INDEX_BLOCK_ALIGN, 2);
        wavHeader.bitsPerSample = readLittleEndianInt(buf, INDEX_BITS_PER_SAMPLE, 2);
        wavHeader.subChunkSize2 = readLittleEndianInt(buf, INDEX_SUB_CHUNK_SIZE_2, 4);
        return wavHeader;
    }

    private static int readLittleEndianInt(byte[] buf, int start, int length){
        int value = 0;
        for(int i = start + length - 1; i >= start; i--){
            value = value << 8;
            value |= (buf[i] & ((1 << 8) - 1));
        }
        return value;
    }

    public static byte[] generateWavHead(int channels, int samplesPerSec, int bitsPerSample, long fileLength){
        byte[] head = Arrays.copyOf(HEAD, HEAD.length);
        setInteger(head, INDEX_CHANNELS, 2, channels);
        setInteger(head, INDEX_SAMPLES_PER_SEC, 4, samplesPerSec);
        setInteger(head, INDEX_BYTES_PER_SEC, 4, bitsPerSample / 8 * channels * samplesPerSec);
        setInteger(head, INDEX_BLOCK_ALIGN, 2, channels * bitsPerSample / 8);
        setInteger(head, INDEX_BITS_PER_SAMPLE, 2, bitsPerSample);
        setInteger(head, INDEX_SUB_CHUNK_SIZE_2, 4, fileLength);
        setInteger(head, INDEX_CHUNK_SIZE, 4, HEAD.length - INDEX_FCC_TYPE + fileLength);
        return head;
    }

    private static void setInteger(byte[] head, int index, int length, long val){
        for(int i = 0; i < length; i++){
            head[index + i] = (byte) (val >> (i * 8));
        }
    }
}
