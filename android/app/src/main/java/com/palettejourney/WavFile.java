package com.palettejourney;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Canonical 16 kHz, mono, signed PCM16 WAV; at most 30 seconds per answer. */
final class WavFile implements AutoCloseable {
    static final int SAMPLE_RATE=16000, MAX_BYTES=960000;
    private final RandomAccessFile output;
    private int bytes;
    WavFile(File file) throws IOException { output=new RandomAccessFile(file,"rw");output.setLength(0);header(0); }
    int bytes() { return bytes; }
    void append(byte[] data,int count) throws IOException {
        if(count<0||count>data.length||(count&1)!=0||bytes+count>MAX_BYTES)throw new IOException("INVALID_PCM_SIZE");
        output.write(data,0,count);bytes+=count;
    }
    private void le(int value,int count) throws IOException { for(int i=0;i<count;i++)output.write((value>>>(i*8))&255); }
    private void header(int length) throws IOException {
        output.seek(0);output.writeBytes("RIFF");le(36+length,4);output.writeBytes("WAVEfmt ");le(16,4);le(1,2);le(1,2);
        le(SAMPLE_RATE,4);le(SAMPLE_RATE*2,4);le(2,2);le(16,2);output.writeBytes("data");le(length,4);
    }
    public void close() throws IOException { try { header(bytes);output.getFD().sync(); }finally{output.close();} }
    private static int readLe(RandomAccessFile file,int count) throws IOException { int n=0;for(int i=0;i<count;i++)n|=file.readUnsignedByte()<<(8*i);return n; }
    static int durationMillis(File file) throws IOException {
        try(RandomAccessFile input=new RandomAccessFile(file,"r")) {
            if(input.length()<46||input.length()>MAX_BYTES+44)throw new IOException("INVALID_WAV");
            byte[] mark=new byte[4];input.readFully(mark);if(!new String(mark,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF"))throw new IOException("INVALID_WAV");
            int size=readLe(input,4);input.readFully(mark);if(!new String(mark,java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE"))throw new IOException("INVALID_WAV");
            input.readFully(mark);if(!new String(mark,java.nio.charset.StandardCharsets.US_ASCII).equals("fmt ")||readLe(input,4)!=16||readLe(input,2)!=1||readLe(input,2)!=1||readLe(input,4)!=SAMPLE_RATE||readLe(input,4)!=SAMPLE_RATE*2||readLe(input,2)!=2||readLe(input,2)!=16)throw new IOException("INVALID_WAV");
            input.readFully(mark);int data=readLe(input,4);
            if(!new String(mark,java.nio.charset.StandardCharsets.US_ASCII).equals("data")||data<2||(data&1)!=0||data+44!=input.length()||size!=data+36)throw new IOException("INVALID_WAV");
            return Math.max(1,data*1000/(SAMPLE_RATE*2));
        }
    }
}
